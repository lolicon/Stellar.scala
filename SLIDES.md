# Static Analysis & Pointer Analysis Concepts & Fundamental

## Static Analysis Background & Contraption [10min]

0. Definition (from wiki)
   Static program analysis is the analysis of computer software performed without executing any programs

1. IR (Intermediate Representation)
   NOT source code NOR AST (to avoid language specific constructs)

2. Soot & TAC / Jimple (Typed TAC)

   > The Jimple code is cleaned for redundant code like unused variables
   > or assignments. An important step in the transformation to Jimple is the **linearization (and naming) of expressions so statements only reference at most 3
   > local variables or constants**. Resulting in a more regular and very convenient
   > representation for performing optimizations. In Jimple an analysis only has to
   > handle the **15** statements in the Jimple representation compared to the more
   > than **200** possible instructions in Java bytecode.

   ```java
   class State {
       Prototype foo(Prototype source) {
           if (alwaysFalse()) {
               return source;
           } else {
               return new Inherited();
           }
       }
   }
   ```

   ```jimple
   // jimple at a glance
   <playground.samples.Stage: playground.samples.Prototype foo(playground.samples.Prototype)> {
     this := @this: playground.samples.Stage // L0
     source := @parameter0: playground.samples.Prototype // L0
     $stack2 = virtualinvoke this.<playground.samples.Stage: boolean alwaysFalse()>() // L39

     // not well-printed for jump label
     if $stack2 == 0 goto $stack3 = new playground.samples.Inherited // L39
     return source // L40
     $stack3 = new playground.samples.Inherited // L42
     specialinvoke $stack3.<playground.samples.Inherited: void <init>()>() // L42
     return $stack3 // L42
   }
   ```

3. Accuracy vs Efficiency | (Must Analysis & May Analysis) |
4. Fixed Point & Lattice Theory

## Pointer Analysis Domains & Concepts [15min]

0. Definition (from wiki)
   A static code analysis technique that establishes which pointers, or heap references, can point to which ~~variables~~ allocation, It is often a component of more complex analyses such as escape analysis

1. Pointers (Reference Types)
   -. Variables likes `a`

   -. Fields like `o1.bar`

   -. model `array-access` as specific fields on that array

   > `arr[5] = a; println(arr[3])` equals `arr.value = a; println(arr.value)`

   -. model `static fields` as fields on a dummy global object

2. Allocation Sites

   ```java
   Source s = null
   Source s1 = new Source() // line 18
   s = s1
   Source s2 = new Source() // line 19
   s = s2

   ...
   void a() {
      method(...)
   }

   void b() {
      method(...)
   }
   ...

   void method(Source s) {
      ...
      Source bar = ... // which allocation does `bar` points to ? (L18 / L19)
      System.out.println(instance.field.bar); // what does `instance.field.bar` may point to ?
      ...
   }

   ```

## Taxonomy [5min]

```java
class Base {
   Source spec = new Source(__LINE__) // line d
   abstract void foo(src: Source)
   void onResume() {
      foo(new Source(__LINE__)); // line b
   }

   void onStart() {
      foo(new Source(__LINE__)); // line c
   }
}

class One extends Base {
   void foo(src: Source)  {
      Source bar = src;
      Log.e(bar.line); // line a1
      bar = new Source(__LINE__); // line a
      Log.e(bar.line); // line a2
   }
}

class Two extends One {
   void foo(src: Source)  {
      Source bar = src;
      Log.e(bar.line); // line t1
      bar = new Source(__LINE__); // line t
      Log.e(bar.line); // line t2
   }
}
```

1. Heap Abstraction (Allocation-Sites) | ...Source Definition?
2. Flow Sensitive | Flow Insensitive (\*\*)
3. Context Sensitive | Context Insensitive (\*\*)
4. Whole-Program Analysis | On-Demand Driven

## Pointer Affecting Statements [15min]

```scala
// New
val x = new Bar()

// Assign
val y = x

// Store
bar.foo = foo

// Load
val z = bar.foo

// Call (virtual / special / static / interface / dynamic)
val x = bar.baz(x, y, ...)
```

> why don't we process statements like `foo.a = bar.b` and `foo.a.b.c = a` explicitly ?
>
> => TAC

## Rules [18min]

1. Andersen's Algorithm (Cubic)
2. SG's Algorithm (Almost linear)

### New

`val x = new Bar() // at line i`

```math
\frac {} {o_i \in pt(x)}
```

### Assign

`val x = y`

```math
\frac {o_i \in pt(y)} {o_i \in pt(x)}
```

### Store

`x.foo = y`

```math
\frac { o_i \in pt(x), o_j \in pt(y)} { o_j \in pt(o_i.foo) }
```

### Load

`val x = y.foo`

```math
\frac {o_i \in pt(y), o_j \in(o_i.foo)} { o_j \in pt(x)}
```

### Call

```scala
def method(p1, p2, p3) {
   if(...) {
      $stack1 = this.foobar;
      return $stack1;
   } else {
      $stack2 = ...
      return $stack2;
   }
}

def bar() = {
   val returns = receiver.method(a1, ... ,an)
}
```

```math
\frac
{ o_i \in pt(receiver),
   \\
   method = Dispatch(o_i, method),
   \\
   o_u \in pt(a_j), 1 \leq j \leq n,
   \\
   o_{returns} \in pt(method_{return})
}
{ o_i \in pt(method_{this}),
  \\
  o_u \in pt(method_{parameter_j}),  1 \leq j \leq n,
  \\
  o_{returns} \in pt(returns)
}
```

for all

```math
a_1 \to method_{parameter_{1}}
\\
...
\\
a_n \to method_{parameter_{n}}
\\
returns \gets method_{return}
```

## A trivial implementation [25min]

[Solver.scala](http://10.117.7.201//natsuki/stellaris/-/blob/master/src/main/scala/app/Solver.scala)

detailed explanation:

1. Types

```scala
  type Allocation(line: Int, clazz: String)

  type Pointer =
     VarPointer(methodName: String, local: String, clazz: String)
   | FieldPointer(alloc: Allocation, fieldName: String)

  type CallSite(receiver: VarPointer?, abstracts: Method, args: Array[Pointer], returns: Pointer?, lineNumber: Int)
```

2. Internal Data Structures

```scala
val reachableMethods = mutable.Set[SootMethod]()
val worklist         = mutable.Queue[(Pointer, mutable.Set[Allocation])]()
val callGraph        = mutable.Set[(CallSite, SootMethod)]()

val pointerGraph     = Graph[Pointer, DiEdge]()
val env              = mutable.Map[Pointer, mutable.Set[Allocation]]().withDefaultValue(mutable.Set[Allocation]())
```

3. Solve & Interactive Visualize

## Fixed Point Theorem & Lattice [Optional]

### iterative algorithm

```math
f(x) = y\\
f^2(x) = f(f(x))\\
...\\
f^n(x) = f(f^{n-1}(x))\\
```

when

```math
f^k(x) = f^{k-1}(x)
```

we say `f` reach its fixed-point

## Flow Sensitive

## Object Sensitive

## Context Sensitive & k-CFA

## Reference:

1. https://en.wikipedia.org/wiki/Pointer_analysis
2. https://pascal-group.bitbucket.io/
3. https://matt.might.net/articles/implementation-of-kcfa-and-0cfa/
4. https://www.cs.cmu.edu/~aldrich/courses/15-819O-13sp/resources/pointer.pdf

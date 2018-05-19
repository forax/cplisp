# cplisp
A Lisp that is able to compile part of itself into the constant pool of a Java .class

It uses the new constant dynamic (condy) constant pool constant,
so if you want to run a .class, you need either to get an early access build of jdk 11,
you can download it at http://jdk.java.net/11/, or use pro (see below).  

# how to build it
I use pro to build the project, Maven can be used to download and run pro, so
```
  mvn install
```

You only need to run Maven once, after that you can use pro 
```
./pro/bin/pro
```

Pro embeds it's own JDK, so you can use pro as a JDK if you do not want to download a jdk 11 and run java using
```
./pro/bin/java
```

# how to run cplisp
```
  /path/to/jdk11/bin/java --module-path target/main/artifact:deps -m fr.umlv.cplisp
```

# how to compile a script as a Java class
first run cplisp, then ask to compile with the function compile
```
  (compile foo (print hello cplisp))
```
it will create a self contained class foo.class on the disk

then you can run the generated foo.class like this
```
  /path/to/jdk11/bin/java foo
```

Using javap, you can see how the expressions are encoded in the constant pool
```
  /path/to/jdk11/bin/javap -verbose -c foo.class
```

```
  ...
  #14 = String             #13            // cplisp
  #15 = Utf8               fr/umlv/cplisp/CpLisp
  #16 = Class              #15            // fr/umlv/cplisp/CpLisp
  #17 = Utf8               bsm
  #18 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #19 = NameAndType        #17:#18        // bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #20 = Methodref          #16.#19        // fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #21 = MethodHandle       6:#20          // REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #22 = Utf8               Ljava/lang/Object;
  #23 = NameAndType        #9:#22         // print:Ljava/lang/Object;
  #24 = ConstantDynamic    #0:#23         // #0:print:Ljava/lang/Object;
  #25 = NameAndType        #7:#22         // eval:Ljava/lang/Object;
  #26 = ConstantDynamic    #1:#25         // #1:eval:Ljava/lang/Object;
  #27 = Utf8               Code
  #28 = Utf8               BootstrapMethods
{
  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=1, locals=1, args_size=1
         0: ldc           #26                 // ConstantDynamic #1:eval:Ljava/lang/Object;
         2: pop
         3: return
}
BootstrapMethods:
  0: #21 REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    Method arguments:
      #10 print
      #12 hello
      #14 cplisp
  1: #21 REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    Method arguments:
      #8 eval
      #24 #0:print:Ljava/lang/Object;
```

One interesting thing is that by default (thanks to ASM) common sub expressions will be encoded only once in the constant pool.
Which mean that the execution of any functions is considered as side effect free, so by example, the following code will print 5 only once 
```
  (compile foo (def a 5) (print a) (def a 7) (print a))
```
because the second call to (print a) will return the previously calculated value of (print a).

So this semantics will only work well for lisps like Clojure that disallow side effect.
The other solution is to change the compiler to generate a new constant if there is a side effect,
with the function print renamed as print! so it's easy to make the difference between a function which has side effect or not.

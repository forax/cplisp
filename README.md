# cplisp
A Lisp (Scheme like) that is able to compile itself into the constant pool of a Java .class

It uses the new constant dynamic (condy) constant pool constant,
so if you want to run a .class, you need to get the source of the project amber
```
  hg clone http://hg.openjdk.java.net/amber/amber
```
then update to move to the condy branch
```
  hg update condy
```
then compile the whole jdk
```
  configure
  make images
```

The patched java launcher with condy is then available at
```
  amber/build/linux-x86_64-normal-server-release/jdk/bin/java
```
  

# how to build it
run the build script, it requires Java 9
```
  sh build.sh
```

# how to run cplisp
```
  java --module-path target/main/artifact:deps -m fr.umlv.cplisp
```

# how to compile a script as a Java class
first run cplisp, then ask to compile with the function compile
```
  (compile foo (print hello cplisp))
```
it will create a file foo.class

then you can run the generated foo.class like this
```
  amber/build/linux-x86_64-normal-server-release/jdk/bin/java -cp .:target/main/artifact/cplisp.jar foo
```

Using javap, you can see how the expressions are encoded in the constant pool
```
  amber/build/linux-x86_64-normal-server-release/jdk/bin/javap -verbose -c -cp .:target/main/artifact/cplisp.jar foo
```

```
  ...
  #15 = Utf8               bsm
  #16 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #17 = NameAndType        #15:#16        // bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #18 = Methodref          #14.#17        // fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #19 = MethodHandle       6:#18          // REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
  #20 = Utf8               Ljava/lang/Object;
  #21 = NameAndType        #9:#20         // eval:Ljava/lang/Object;
  #22 = ConstantDynamic    #0:#21         // #0:eval:Ljava/lang/Object;
  #23 = Utf8               cplisp
  #24 = String             #23            // cplisp
  #25 = ConstantDynamic    #1:#21         // #1:eval:Ljava/lang/Object;
  #26 = NameAndType        #7:#20         // print:Ljava/lang/Object;
  #27 = ConstantDynamic    #2:#26         // #2:print:Ljava/lang/Object;
  #28 = Utf8               Code
  #29 = Utf8               BootstrapMethods
{
  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: (0x0009) ACC_PUBLIC, ACC_STATIC
    Code:
      stack=1, locals=1, args_size=1
         0: ldc           #27                 // ConstantDynamic #2:print:Ljava/lang/Object;
         2: pop
         3: return
}
BootstrapMethods:
  0: #19 REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    Method arguments:
      #10 eval
      #12 hello
  1: #19 REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    Method arguments:
      #10 eval
      #24 cplisp
  2: #19 REF_invokeStatic fr/umlv/cplisp/CpLisp.bsm:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
    Method arguments:
      #8 print
      #22 #0:eval:Ljava/lang/Object;
      #25 #1:eval:Ljava/lang/Object;
```

One interesting thing is that by default (thanks to ASM) common sub expressions will be encoded only once in the constant pool. Which mean that the execution of all functions is considered as side effect free, so by example, the following code will print 5
```
  (compile foo (def a 5) (print a) (def a 7) (print a))
```
because the second call to (print a) will return the previously calculated value of (print a).

So this semantics will only work well for lisps like Clojure that disallow side effect.
The other solution is to change the compilation to generate a new constant if there is a side effect, with the function print renamed as print! so it's easy to make the difference between a function which has side effect or not.



# cplisp
A Lisp that is able to compile part of itself into the constant pool of a Java .class

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

One interesting thing is that by default (thanks to ASM) common sub expressions will be encoded only once in the constant pool. Which mean that the execution of all functions is considered as side effect free, so by example, the following code will print 5
```
  (compile foo (def a 5) (print a) (def a 7) (print a))
```
because the second call to (print a) will return the previously calculated value of (print a).

So this semantics will only work well for lisps like Clojure that disallow side effect.
The other solution is to change the compilation to generate a new constant if there is a side effect, with the function print renamed as print! so it's easy to make the difference between a function which has side effect or not.



package com.github.forax.cplisp;

import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM6;
import static org.objectweb.asm.Opcodes.ASM7_EXPERIMENTAL;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V11;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

@SuppressWarnings("deprecation") // usage of ASM experimental APIs
public class CpLisp {
  static class Tokenizer {
    private final Reader reader;
    private final CharBuffer buffer = CharBuffer.allocate(8192);
    
    private String nextToken;
    private final StringBuilder builder = new StringBuilder();
    
    public Tokenizer(Reader reader) {
      this.reader = reader;
      buffer.position(buffer.limit());
    }
    
    private int get() {
      if (!buffer.hasRemaining()) {
        buffer.clear();
        try {
          if (reader.read(buffer) == -1) {
            return -1;
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        buffer.flip();
      }
      return buffer.get();
    }
    
    public String token() {
      String token;
      if (nextToken != null) {
        token = nextToken;
        nextToken = null;
        return token;
      }
      
      int c;
      loop: for(;;) {
        switch(c = get()) {
        case '(':
        case ')':
          token = "" + (char)c;
          break loop;
        case -1:
          token = EOF;
          break loop; 
        default:
          if (c <= 32 || c == ',') { // blank
            if (builder.length() != 0) {
              token = null;
              break loop;
            }
            continue loop;
          }
          builder.append((char)c);
          continue loop;
        }
      }
      if (builder.length() != 0) {
        nextToken = token;
        token = builder.toString(); 
        builder.setLength(0);
      }
      return token;
    }
  }
  
  static class Parser {
    static final Parser PARSER = new Parser(new Tokenizer(new InputStreamReader(System.in)));
    
    private final Tokenizer tokenizer;

    public Parser(Tokenizer tokenizer) {
      this.tokenizer = tokenizer;
    }
    
    private static Object atom(String token) {
      //System.out.println("atom.token " + token);
      
      try {
        return Integer.parseInt(token);
      } catch(@SuppressWarnings("unused") NumberFormatException e) {
        return token;
      }
    }
    
    public Object parse(ArrayList<Object> group) {
      loop: for(;;) {
        String token;
        switch(token = tokenizer.token()) {
        case "(": {
          Object result = parse(new ArrayList<>());
          if (group == null) {
            return result;
          }
          group.add(result);
          continue loop;
        }
        case ")":
          return (group == null)? new ArrayList<>(): group;
        case EOF:
          return EOF;
        default: 
          Object atom = atom(token);
          if (group == null) {
            return atom;
          }
          group.add(atom);
          continue loop;
        }
      }
    }
  }
  
  
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Builtin {
    String value() default "";
  }
  
  static final String EOF = "";
  static final ThreadLocal<Map<String, Object>> ENV;
  static {
    Map<String, Object> global = newScope(null);
    for(Method m: CpLisp.class.getMethods()) {
      if ((m.getModifiers() & (ACC_PUBLIC|ACC_STATIC)) != (ACC_PUBLIC|ACC_STATIC) || m.getName().equals("main")) {
        continue;
      }
      
      // in compiled mode, we don't want dependency, so Builtin is renamed to Deprecated
      String builtinName =
        Optional.ofNullable(m.getAnnotation(Deprecated.class))
          .map(Deprecated::since)
          .orElseGet(() -> m.getAnnotation(Builtin.class).value());
      
      MethodHandle mh = method(m);
      global.put(m.getName(), mh);
      Optional.of(builtinName).filter(v -> !v.isEmpty()).ifPresent(name -> global.put(name, mh));
    }
    ENV = ThreadLocal.withInitial(() -> newScope(global));
  }
  
  static Map<String, Object> newScope(Map<String, Object> parent) {
    HashMap<String, Object> map = new HashMap<>();
    map.put("__parent", parent);
    return map;
  }
  @SuppressWarnings("unchecked")
  private static Object getInScope(Map<String, Object> scope, String name) {
    do {
      Object o = scope.get(name);
      if (o != null) {
        return o;
      }
      scope = (Map<String, Object>)scope.get("__parent");
    } while (scope != null);
    return null;
  }
  
  private static MethodHandle[] accessors(Field field) {
    Lookup lookup = MethodHandles.lookup();
    try {
      if (Modifier.isFinal(field.getModifiers())) {
        return new MethodHandle[] { lookup.unreflectGetter(field) };
      }
      return new MethodHandle[] { lookup.unreflectGetter(field), lookup.unreflectSetter(field) };
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError)new IllegalAccessError().initCause(e);
    }
  }
  private static MethodHandle method(Method method) {
    try {
      return MethodHandles.lookup().unreflect(method);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError)new IllegalAccessError().initCause(e);
    }
  }
  private static MethodHandle constructor(Constructor<?> constructor) {
    try {
      return MethodHandles.lookup().unreflectConstructor(constructor);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError)new IllegalAccessError().initCause(e);
    }
  }
  
  public static @Builtin Object read() {
    return Parser.PARSER.parse(null);
  }
  
  private static String str(Object o) {
    if (o instanceof List) {
      return ((List<?>)o).stream().map(CpLisp::str).collect(joining(", ", "(", ")"));
    }
    return String.valueOf(o);
  }
  
  public static @Builtin void print(List<?> list) {
    System.out.println(list.stream().map(CpLisp::eval).map(CpLisp::str).collect(joining(" ")));
  }
  
  public static @Builtin Object eval(Object o) {
    //System.out.println("eval " + o);
    
    if (o instanceof List) {
      List<?> list = (List<?>)o;
      if (list.isEmpty()) {
        return list;
      }
      Object first = list.get(0);
      if (first instanceof MethodHandle) {  // native call
        return call(first, list.stream().skip(1).map(CpLisp::eval).collect(toList()));
      }
      if (first instanceof String) {        // delay evaluation
        return call(first, list.stream().skip(1).collect(toList()));
      }
      return list;
    }
    if (o instanceof String) {
      String name = o.toString();
      return Optional.ofNullable(getInScope(ENV.get(), name)).orElse(name);
    }
    return o;
  }
  
  public static @Builtin("+") Object add(List<?> args) {
    return args.stream().map(CpLisp::eval).mapToInt(v -> (int)v).reduce(Integer::sum).orElse(0);
  }
  
  public static @Builtin("def") void define(String name, Object o) {
    ENV.get().put(name, eval(o));
  }
  
  public static @Builtin MethodHandle lambda(List<?> args) {
    List<?> parameters = (List<?>)args.get(0);
    List<?> body = args.stream().skip(1).collect(toList());
    return asInterpret(parameters, body, ENV.get());
  }
  
  private static final MethodHandle INTERPRET;
  static {
    try {
      INTERPRET = MethodHandles.lookup().findStatic(CpLisp.class, "interpret",
          methodType(Object.class, List.class, List.class, Map.class, List.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  static MethodHandle asInterpret(List<?> parameters, List<?> body, Map<String, Object> scope) {
    return MethodHandles.insertArguments(INTERPRET, 0, parameters, body, scope);
  }

  @SuppressWarnings("unused")
  private static Object interpret(List<?> parameters, List<?> body, Map<String, Object> parent, List<?> args) {
    if (parameters.size() != args.size()) {
      throw new IllegalArgumentException("wrong number of arguments " + args.size() + " expect " + parameters.size());
    }
    Map<String, Object> scope = newScope(parent);
    for(int i = 0; i < parameters.size(); i++) {
      scope.put(parameters.get(i).toString(), args.get(i));
    }

    Map<String, Object> savedScope = ENV.get();
    ENV.set(scope);
    Object result = null;
    try {
      for(Object instr: body) {
        result = eval(instr);
      }
    } finally {
      ENV.set(savedScope);
    }
    return result;
  }
  
  private static Object call(Object fun, List<?> args) {
    //System.out.println("call: " + fun + " " + o);
    
    if (fun instanceof String) {
      String funName = fun.toString();
      fun = getInScope(ENV.get(), funName);
      if (fun == null) {
        throw new RuntimeException("unknown function " + funName);
      }
    }
    if (!(fun instanceof MethodHandle)) {
      throw new RuntimeException(fun + " is not a function ");
    }
    MethodHandle mh = (MethodHandle)fun;
    
    //System.out.println("call: found " + mh);
    
    try {
      if (mh.type().parameterCount() == 1 && mh.type().parameterType(0) == List.class) {
        return mh.invoke(args);  
      }
      return mh.invokeWithArguments(args);
    } catch(RuntimeException | Error e) {
      throw e;
    } catch(Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
  
  static final MethodHandle MULTICALL;
  static {
    try {
      MULTICALL = MethodHandles.lookup().findStatic(CpLisp.class, "multicall", MethodType.methodType(Object.class, List.class, MethodHandle[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static boolean isAssignableFrom(MethodType methodType, Class<?>[] argTypes) {
    if (methodType.parameterCount() != argTypes.length) {
      return false;
    }
    for(int i = 0; i < argTypes.length; i++) {
      if (!methodType.parameterType(i).isAssignableFrom(argTypes[i])) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unused")  // called by a MethodHandle
  private static Object multicall(List<?> args, MethodHandle[] mhs) throws Throwable {
    Class<?>[] argTypes = new Class<?>[args.size()];
    for(int  i = 0; i < args.size(); i++) {
      Object arg = args.get(i);
      argTypes[i] = (arg == null)? null: arg.getClass();
    }
    for(MethodHandle mh: mhs) {
      if (isAssignableFrom(mh.type(), argTypes)) {
        return mh.invokeWithArguments(args);
      }
    }
    throw new WrongMethodTypeException(Arrays.toString(argTypes) + " incompatible with " + Arrays.toString(mhs));
  }

  static MethodHandle asMultiCallMH(MethodHandle[] mhs) {
    return MethodHandles.insertArguments(MULTICALL, 1, new Object[] { mhs });
  }
  
  public static @Builtin("import") void alias(String className, String alias) {
    Class<?> type;
    try {
      type = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    
    // constructors
    ENV.get().put(alias, asMultiCallMH(Arrays.stream(type.getConstructors()).map(CpLisp::constructor).toArray(MethodHandle[]::new)));
    
    // fields
    Arrays.stream(type.getFields()).forEach(f -> ENV.get().put(alias + "-" + f.getName(), asMultiCallMH(CpLisp.accessors(f))));
    
    // methods
    Arrays.stream(type.getMethods()).collect(Collectors.groupingBy(m -> m.getName()))
          .forEach((name, ms) -> ENV.get().put(alias + "-" + name, asMultiCallMH(ms.stream().map(CpLisp::method).toArray(MethodHandle[]::new))));
  }
  

  static class Compiler {
    static final String CPLISP_NAME = CpLisp.class.getName().replace('.', '/');
    static final String BUILTIN_DESC = 'L' + Builtin.class.getName().replace('.', '/') + ';';

    /*private static Handle handle(String name) {
      Object o = ENV.get().get(name);
      MethodHandle mh = (MethodHandle)o;
      return new Handle(H_INVOKESTATIC, CPLISP_NAME, name, mh.type().toMethodDescriptorString(), false);
    }*/

    private static Object constant(Object o, Handle bsm) {
      String function;
      Stream<?> stream;
      if (o instanceof List) {
        List<?> list = (List<?>)o;
        Object first;
        if (list.isEmpty() || !((first = list.get(0)) instanceof String)) {
          function = "list";
          stream = list.stream();
        } else {
          function = first.toString();
          stream = list.stream().skip(1);
        }
        stream = stream.map(v -> constant(v, bsm));
      } else {
        return o;
      }
      return new ConstantDynamic(function, "Ljava/lang/Object;", bsm, Stream.concat(Stream.of(function), stream).toArray());
    }
    
    static void compile(List<?> args) {
      byte[] bytes;
      try {
        bytes = CpLisp.class.getClassLoader().getResourceAsStream(CPLISP_NAME + ".class").readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      
      String className = args.get(0).toString();
      ClassReader reader = new ClassReader(bytes);
      ClassWriter writer = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
      writer.visit(V11, ACC_PUBLIC, className, null, "java/lang/Object", null);
      
      String classInternalName = className.replace('.', '/');
      ClassVisitor cv = new ClassRemapper(ASM7_EXPERIMENTAL, writer, new Remapper() {
        @Override
        public String map(String typeName) {
          return (typeName.equals(CPLISP_NAME))? classInternalName: typeName;
        }
      }) { /* empty */ };
      
      reader.accept(new ClassVisitor(ASM7_EXPERIMENTAL, cv) {
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
          // filter out class info
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          if (name.equals("main") && desc.equals("([Ljava/lang/String;)V")) {        // filter out REPL main
            return null;
          }
          return new MethodVisitor(ASM7_EXPERIMENTAL, super.visitMethod(access, name, desc, signature, exceptions)) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) { // rename Builtin to Deprecated
              if (desc.equals(BUILTIN_DESC)) {
                return new AnnotationVisitor(ASM6, super.visitAnnotation("Ljava/lang/Deprecated;", visible)) {
                  @Override
                  public void visit(String name, Object value) {
                    super.visit("since", value);
                  }
                };
              }
              return super.visitAnnotation(desc, visible);
            }
          };
        }
      }, 0);
      

      Handle bsm = new Handle(H_INVOKESTATIC, classInternalName, "bsm",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
      
      MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      
      for(int i = 1; i < args.size(); i++) {
        Object arg = args.get(i);
        mv.visitLdcInsn(constant(List.of("eval", arg), bsm));
        mv.visitInsn(POP);
      }
      
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
      
      cv.visitEnd();
      
      Path path = Paths.get(className + ".class");
      try {
        Files.write(path, writer.toByteArray());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      System.out.println(path + " generated");  
    }
  }
  
  @SuppressWarnings("unused")  // called by the different constant dynamics
  private static Object bsm(Lookup lookup, String name, Class<?> type, Object fun, Object... args) throws Throwable {
    return call(fun, new ArrayList<>(Arrays.asList(args)));
  }
  
  public static @Builtin void compile(List<?> args) {
    Compiler.compile(args);
  }
  
  public static void main(String[] args) {
    System.out.println("Welcome to Constant Pool Lisp");
    
    Object o;
    while((o = read()) != EOF) {
      //System.out.println("read: " + o);
      
      Object result;
      try {
        result = eval(o);
      } catch(RuntimeException e) {
        //System.out.println(e);
        e.printStackTrace();
        continue;
      }
      if (result != null) {
        print(List.of(result));
      }
    }
  }
}

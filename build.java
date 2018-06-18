import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;
import static java.nio.file.Files.*;
import static com.github.forax.pro.helper.FileHelper.*;

class build {
  public static void main(String[] args) {

    var distribute = command(() -> {
      deleteAllFiles(location("cplisp"), true);
      move(location("target/image/bin/com.github.forax.cplisp"), location("target/image/bin/cplisp"));
      move(location("target/image"), location("cplisp"));
    });

    resolver.
      //checkForUpdate(true).
      //remoteRepositories(uri("https://repository.ow2.org/nexus/content/repositories/snapshots")).
      dependencies(
        // ASM
        "org.objectweb.asm:6.2",
        "org.objectweb.asm.tree:6.2",
        "org.objectweb.asm.tree.analysis:6.2",
        "org.objectweb.asm.util:6.2",
        "org.objectweb.asm.commons:6.2"
      );

    packager.
      modules(
        "com.github.forax.cplisp@1.1/com.github.forax.cplisp.CpLisp"
      );   

    run(resolver, modulefixer, compiler, packager, linker, distribute);

}}

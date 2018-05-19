import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

resolver.
    //checkForUpdate(true).
    remoteRepositories(list(uri("https://repository.ow2.org/nexus/content/repositories/snapshots"))).
    dependencies(list(
        // ASM
        "org.objectweb.asm=org.ow2.asm:asm:6.2-SNAPSHOT",
        "org.objectweb.asm.tree=org.ow2.asm:asm-tree:6.2-SNAPSHOT",
        "org.objectweb.asm.tree.analysis=org.ow2.asm:asm-analysis:6.2-SNAPSHOT",
        "org.objectweb.asm.util=org.ow2.asm:asm-util:6.2-SNAPSHOT",
        "org.objectweb.asm.commons=org.ow2.asm:asm-commons:6.2-SNAPSHOT"
    ))
   
packager.
    modules(list(
        "com.github.forax.cplisp@1.1/com.github.forax.cplisp.CpLisp"
    ))   
    
run(resolver, modulefixer, compiler, packager)

/exit

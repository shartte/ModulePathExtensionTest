package net.neoforged.mpextension.startup;

import net.neoforged.jarjar.nio.layzip.LayeredZipFileSystemProvider;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class MpExtensionTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("MpExtensionTest: " + MpExtensionTest.class.getModule());
        System.out.println("MpExtensionTest: " + MpExtensionTest.class.getClassLoader());

        Instrumentation instrumentation = MpAgent.instrumentation;
        if (instrumentation == null) {
            System.err.println("Agent not installed");
            System.exit(1);
        }

        ClassLoader systemCl = ClassLoader.getSystemClassLoader();

        Consumer<ModuleReference> moduleAdder = getModuleAdder(instrumentation, systemCl);

        // Define our injection module layer
        File jarJar = null;
        String[] classPath = System.getProperty("java.class.path").split(File.pathSeparator);
        if (classPath.length == 1) {
            Path mainJar = Paths.get(classPath[0]);
            // Most likely launched via -jar
            Manifest mf;
            try (JarInputStream jin = new JarInputStream(Files.newInputStream(mainJar))) {
                mf = jin.getManifest();
            }
            if (mf != null) {
                String classPathAttr = mf.getMainAttributes().getValue("Class-Path");
                if (classPathAttr != null) {
                    classPath = classPathAttr.split("\\s+");
                    for (int i = 0; i < classPath.length; i++) {
                        classPath[i] = mainJar.resolveSibling(classPath[i]).toString();
                    }
                }
            }
        }
        for (String s : classPath) {
            File f = new File(s);
            if (f.isFile() && f.getName().contains("JarJar")) {
                jarJar = f;
            }
        }

        if (jarJar == null) {
            System.err.println("JJ is not on the CP!");
            System.exit(1);
        }

        List<Configuration> parents = new ArrayList<>();
        parents.add(ModuleLayer.boot().configuration());
        Configuration cf = Configuration.resolveAndBind(
                ModuleFinder.of(jarJar.toPath()),
                parents,
                ModuleFinder.of(),
                List.of("JarJarFileSystems")
        );

        ModuleLayer.defineModules(
                cf,
                Collections.singletonList(ModuleLayer.boot()),
                s -> systemCl
        );

        for (ResolvedModule module : cf.modules()) {
            moduleAdder.accept(module.reference());
        }

        for (FileSystemProvider installedProvider : FileSystemProvider.installedProviders()) {
            if (installedProvider != FileSystems.getDefault().provider()) {
                System.out.println("Provider " + installedProvider.getClass());
                System.out.println(" module: " + installedProvider.getClass().getModule());
                System.out.println(" loader: " + installedProvider.getClass().getClassLoader());
            }
        }

        System.out.println(LayeredZipFileSystemProvider.class.getModule());
        System.out.println(LayeredZipFileSystemProvider.class.getClassLoader());

        if (!"JarJarFileSystems".equals(LayeredZipFileSystemProvider.class.getModule().getName())) {
            System.exit(1);
        }
    }

    private static Consumer<ModuleReference> getModuleAdder(Instrumentation instrumentation, ClassLoader systemCl) throws Exception {
        instrumentation.redefineModule(
                systemCl.getClass().getModule(),
                Set.of(),
                Map.of(),
                Map.of(
                        systemCl.getClass().getPackageName(), Set.of(MpExtensionTest.class.getModule())
                ),
                Set.of(),
                Map.of()
        );

        Method loadModule = systemCl.getClass().getMethod("loadModule", ModuleReference.class);
        return moduleReference -> {
            try {
                loadModule.invoke(systemCl, moduleReference);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
    }
}

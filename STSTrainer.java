import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javassist.*;
import javassist.expr.*;

public class STSTrainer {

    private static final String LOG_FILE = "ststrainer.log";
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void log(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = dtf.format(LocalDateTime.now());
            out.println("[" + timestamp + "] " + message);
            out.flush();
        } catch (Exception e) {
            System.err.println("Logger Error: " + e.getMessage());
        }
    }

    private static final Set<String> WHITE_LIST = new HashSet<>(Arrays.asList(
            "com.megacrit.cardcrawl",
            "com.evacipated.cardcrawl.modthespire",
            "com.megacrit.mtslauncher"));

    public static boolean isWhitelisted(String className) {
        if (className == null)
            return false;
        return WHITE_LIST.stream().anyMatch(className::startsWith);
    }

    private static String agentJarPath = "";

    public static void premain(String agentArgs, Instrumentation inst) {
        log("=== Agent Loaded (premain) ===");
        try {
            File f = new File(STSTrainer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            agentJarPath = f.getAbsolutePath();
            log("[STSTrainer] Agent Path Located: " + agentJarPath);
        } catch (Exception e) {
            agentJarPath = "STSTrainer.jar";
        }

        boolean canRetransform = inst.isRetransformClassesSupported();

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {

                if (className == null)
                    return null;
                String dotName = className.replace("/", ".");

                if (!isWhitelisted(dotName))
                    return null;

                try {
                    ClassPool cp = ClassPool.getDefault();
                    if (loader != null) {
                        cp.insertClassPath(new LoaderClassPath(loader));
                    }

                    CtClass cc = cp.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

                    boolean changed = false;

                    // --- 修改 ModTheSpire (MTS) ---
                    if (dotName.equals("com.evacipated.cardcrawl.modthespire.Loader")
                            || dotName.equals("com.megacrit.mtslauncher.Main")) {
                        log("Attempting to patch MTS Loader...");
                        CtMethod mainMethod = cc.getDeclaredMethod("main");
                        mainMethod.instrument(new ExprEditor() {
                            public void edit(NewExpr e) throws CannotCompileException {
                                if (e.getClassName().equals("java.lang.ProcessBuilder")) {
                                    CtClass[] params = null;
                                    try {
                                        params = e.getConstructor().getParameterTypes();
                                    } catch (Exception ex) {
                                        throw new CannotCompileException(ex);
                                    }
                                    boolean isListConstructor = params != null && params.length > 0
                                            && params[0].getName().equals("java.util.List");
                                    log("[STSTrainer] Found ProcessBuilder constructor with param"
                                            + params[0].getName() + " at " + e.getLineNumber() + " in "
                                            + e.getFileName());
                                    // @formatter:off
                                    e.replace("{ " +
                                            "  java.util.List cmdList; " +
                                            (isListConstructor ? 
                                            "  cmdList = (java.util.List)$1; " :
                                            "  cmdList = new java.util.ArrayList(java.util.Arrays.asList($1)); ") +
                                            "  for (int i = 0; i < cmdList.size(); i++) { " +
                                            "    String cmd = cmdList.get(i).toString(); " +
                                            "    if (cmd.endsWith(\"java\") || cmd.endsWith(\"java.exe\")) { " +
                                            "      System.out.println(\"[STSTrainer] Intercepting MTS Launch!\"); " +
                                            "      System.out.println(\"[STSTrainer] Injecting: -javaagent:\" + \""
                                            + agentJarPath.replace("\\", "\\\\") + "\"); " +
                                            "      cmdList.add(i + 1, \"-javaagent:"
                                            + agentJarPath.replace("\\", "\\\\") + "\"); " +
                                            "      break; " +
                                            "    } " +
                                            "  } " +
                                            (isListConstructor ? 
                                            "  $_ = $proceed(cmdList); " :
                                            "  $_ = $proceed((String[])cmdList.toArray(new String[0])); ") +
                                            "}");
                                    // @formatter:on
                                }
                            }
                        });
                        log("MTS Loader patched successfully.");
                        changed = true;
                    }

                    // --- 修改 AbstractCard ---
                    if (dotName.equals("com.megacrit.cardcrawl.cards.AbstractCard")) {
                        log("Patching AbstractCard...");
                        CtMethod m = cc.getDeclaredMethod("freeToPlay");
                        m.insertBefore(
                                "{ if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.player != null) return true; }");
                        changed = true;
                    }

                    // --- 修改 玩家类 ---
                    if (dotName.contains("com.megacrit.cardcrawl.characters")) {
                        if (isSubclassOf(cc, "com.megacrit.cardcrawl.characters.AbstractPlayer")) {
                            log("Patching Player Class: " + dotName);

                            CtMethod mGain = cc.getDeclaredMethod("gainGold");
                            mGain.insertBefore("{ $1 = $1 * 100; }");

                            CtMethod mDamage = cc.getDeclaredMethod("damage");
                            mDamage.instrument(new ExprEditor() {
                                public void edit(FieldAccess f) throws CannotCompileException {
                                    if (f.getFieldName().equals("currentHealth") && f.isReader()) {
                                        f.replace("{ " +
                                                "int realHp = $proceed($$); " +
                                                "if (realHp < 1) { this.heal(this.maxHealth); $_ = this.currentHealth; } "
                                                +
                                                "else { $_ = realHp; } " +
                                                "}");
                                    }
                                }
                            });

                            changed = true;
                        }
                    }

                    if (changed) {
                        byte[] b = cc.toBytecode();
                        cc.detach();
                        return b;
                    }

                } catch (Exception e) {
                    log("[STSTrainer] Error transforming " + dotName + ": " + e.getMessage());
                }
                return null;
            }

            private boolean isSubclassOf(CtClass clazz, String superClassName) {
                try {
                    CtClass current = clazz;
                    while (current != null) {
                        if (current.getName().equals(superClassName))
                            return true;
                        current = current.getSuperclass();
                    }
                } catch (Exception e) {
                }
                return false;
            }
        }, canRetransform);

        if (canRetransform) {
            for (Class<?> clazz : inst.getAllLoadedClasses()) {
                if (isWhitelisted(clazz.getName()) && inst.isModifiableClass(clazz)) {
                    try {
                        inst.retransformClasses(clazz);
                    } catch (UnmodifiableClassException e) {
                        log("[STSTrainer] Error: Class " + clazz.getName() + " is protected and cannot be modified.");
                    } catch (UnsupportedOperationException e) {
                        log("[STSTrainer] Fatal: Retransform failed! Check if 'Can-Retransform-Classes: true' is in MANIFEST.MF.");
                    } catch (Throwable t) {
                        log("[STSTrainer] Unexpected error during retransform of " + clazz.getName() + ": "
                                + t.getMessage());
                    }
                }
            }
        } else {
            log("[STSTrainer] Warning: JVM does not support retransformation.");
        }
    }
}
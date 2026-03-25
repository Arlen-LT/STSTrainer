import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import javassist.*;
import javassist.expr.*;

public class STSTrainer {
    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {

                try {
                    ClassPool cp = ClassPool.getDefault();
                    cp.insertClassPath(new LoaderClassPath(loader));
                    CtClass cc = cp.get(className.replace("/", "."));

                    // 核心逻辑：判断这个类是不是 AbstractPlayer 或者它的子类
                    boolean isPlayer = false;
                    CtClass current = cc;
                    while (current != null) {
                        if (current.getName().equals("com.megacrit.cardcrawl.characters.AbstractPlayer")) {
                            isPlayer = true;
                            break;
                        }
                        current = current.getSuperclass();
                    }

                    if (isPlayer && !cc.isInterface()) {
                        try {
                            CtMethod mGain = cc.getDeclaredMethod("gainGold");
                            mGain.insertBefore("{ $1 = $1 * 100; }");

                            CtMethod m = cc.getDeclaredMethod("damage");
                            m.instrument(new ExprEditor() {
                                public void edit(FieldAccess f) throws CannotCompileException {
                                    if (f.getFieldName().equals("currentHealth") && f.isReader()) {
                                        f.replace("{ " +
                                                "int realHp = $proceed($$); " +
                                                "if (realHp < 1) { " +
                                                "  this.heal(this.maxHealth); " +
                                                "  $_ = this.currentHealth; " +
                                                "} else { " +
                                                "  $_ = realHp; " +
                                                "} " +
                                                "}");
                                    }
                                }
                            });

                            byte[] b = cc.toBytecode();
                            cc.detach();
                            return b;
                        } catch (Exception e) {
                            // 某些方法可能不存在，忽略即可
                        }
                    }
                } catch (Exception e) {
                    // 忽略非游戏类的加载异常
                }

                // JVM 内部类名使用 /，而不是 .
                if ("com/megacrit/cardcrawl/cards/AbstractCard".equals(className)) {
                    try {
                        ClassPool cp = ClassPool.getDefault();
                        // 关键：告诉 Javassist 去哪找游戏的类
                        cp.insertClassPath(new LoaderClassPath(loader));

                        CtClass cc = cp.get("com.megacrit.cardcrawl.cards.AbstractCard");
                        CtMethod m = cc.getDeclaredMethod("freeToPlay");

                        // 进阶版手术
                        m.insertBefore(
                                "{ if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.player != null) return true; }");

                        byte[] byteCode = cc.toBytecode();
                        cc.detach(); // 释放内存
                        return byteCode;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        });
    }
}
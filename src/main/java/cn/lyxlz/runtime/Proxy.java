package cn.lyxlz.runtime;

import cn.lyxlz.runtime.anno.RunTime;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

import java.lang.instrument.Instrumentation;

public class Proxy {
    public static void premain(String args, Instrumentation inst) {
        // 添加类加载过滤器
        inst.addTransformer((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            // 创建类池
            ClassPool pool = ClassPool.getDefault();
            try {
                // 获取当前加载的类
                CtClass ctClass = pool.get(className.replaceAll("/", "."));
                // 获取类中的所有方法
                CtMethod[] methods = ctClass.getMethods();
                for (CtMethod method : methods) {
                    // 判断是否包含指定注解
                    Object annotation = method.getAnnotation(RunTime.class);
                    if (annotation == null) continue;
                    // 如果包含，先复制一份方法，方便方法前后位置的增强
                    CtMethod copyMethod = CtNewMethod.copy(method, ctClass, null);
                    // 设置被增强方法名
                    String copyMethodName = copyMethod.getName() + "$agent";
                    copyMethod.setName(copyMethodName);
                    // 将agent方法添加回类
                    ctClass.addMethod(copyMethod);
                    // 改变原有方法
                    method.setBody("{ " +
                            "long begin = System.currentTimeMillis();" +
                            copyMethodName + "();" +
                            "System.out.print(\"程序运行：\");" +
                            "System.out.println(System.currentTimeMillis() - begin + \" ms\");" +
                            " }");
                    return ctClass.toBytecode();
                }
            } catch (Exception ignored) {

            }
            return null;
        });
    }
}

package com.newrelic.labs.test;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.logging.Logger;

public class LoggingAgent {
    private static final Logger logger = Logger.getLogger(LoggingAgent.class.getName());

    public static void premain(String agentArgs, Instrumentation inst) {
        logger.info("LoggingAgent initialized");

        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                String formattedClassName = className.replace('/', '.');
                
                if (formattedClassName.startsWith("com.newrelic.labs.sample")) {
                    logger.info("Transforming class: " + formattedClassName);
                    // Simple demonstration: no bytecode manipulation
                    return classfileBuffer;
                }
                return null;
            }
        };

        // Add transformer without specifying retransformable
        inst.addTransformer(transformer);
    }
}
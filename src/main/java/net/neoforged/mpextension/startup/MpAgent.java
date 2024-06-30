package net.neoforged.mpextension.startup;

import java.lang.instrument.Instrumentation;

public class MpAgent {
    static Instrumentation instrumentation;
    public static void premain(String arguments, Instrumentation instrumentation) {
        MpAgent.instrumentation = instrumentation;
    }
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        MpAgent.instrumentation = instrumentation;
    }
}

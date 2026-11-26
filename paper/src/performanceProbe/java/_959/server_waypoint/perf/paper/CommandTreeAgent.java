package _959.server_waypoint.perf.paper;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Premain-only observation of the pinned Paper 1.21.11 build 111 boundaries. */
public final class CommandTreeAgent {
    private static final String HOOK = "_959/server_waypoint/perf/paper/CommandTreeHooks";
    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        // Paperclip deliberately isolates the server from the system class loader.
        // Expose only JDK-only hooks to bootstrap, never the Bukkit plugin or ASM.
        java.nio.file.Path bridge = java.nio.file.Path.of("command-tree-hooks.jar");
        String resource = "/_959/server_waypoint/perf/paper/CommandTreeHooks.class";
        try (var output = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(bridge));
                var source = CommandTreeAgent.class.getResourceAsStream(resource)) {
            output.putNextEntry(new java.util.jar.JarEntry(resource.substring(1)));
            source.transferTo(output);
            output.closeEntry();
        }
        instrumentation.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(bridge.toFile()));
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!name.equals("net/minecraft/commands/Commands")) {
                    return null;
                }
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                int[] boundaries = new int[4];
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                        String phase = switch (method + descriptor) {
                            case "sendCommands(Lnet/minecraft/server/level/ServerPlayer;)V" -> "dispatch";
                            case "sendAsync(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Collection;)V" -> "async";
                            case "runSync(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Collection;Lcom/mojang/brigadier/tree/RootCommandNode;)V" -> "sync";
                            default -> null;
                        };
                        if (phase == null) {
                            return delegate;
                        }
                        boundaries[switch (phase) { case "dispatch" -> 0; case "async" -> 1; default -> 2; }]++;
                        return new MethodVisitor(Opcodes.ASM9, delegate) {
                            private void hook(String operation) {
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitLdcInsn(operation);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "observe", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
                            }
                            @Override public void visitCode() {
                                super.visitCode();
                                hook(phase + "Start");
                            }
                            @Override public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    hook(phase + "End");
                                }
                                super.visitInsn(opcode);
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                                boolean send = phase.equals("sync") && owner.equals("net/minecraft/server/network/ServerGamePacketListenerImpl")
                                        && name.equals("send") && desc.equals("(Lnet/minecraft/network/protocol/Packet;)V");
                                if (send) {
                                    boundaries[3]++;
                                    super.visitInsn(Opcodes.DUP);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "packet", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                                }
                                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                                if (send) {
                                    hook("handoff");
                                }
                            }
                        };
                    }
                }, 0);
                boolean transformed = java.util.Arrays.equals(boundaries, new int[]{1, 1, 1, 1});
                CommandTreeHooks.transformed(transformed);
                // Unsupported shapes are not transformed and cannot produce valid evidence.
                return transformed ? writer.toByteArray() : null;
            }
        });
    }

}

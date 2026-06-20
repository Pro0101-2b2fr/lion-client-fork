package com.lionclient.gui;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Minimal GLSL post-processing program wrapper (fragment shader only — the
 * fixed-function vertex pipeline provides {@code gl_TexCoord} for fullscreen
 * quads). Compilation is lazy and failure-tolerant: if the driver rejects the
 * shader, the program stays at 0 and callers simply skip the effect.
 */
public final class ShaderUtils {
    private final String fragmentSource;
    private int program;
    private boolean attempted;
    private boolean ok;

    public ShaderUtils(String fragmentSource) {
        this.fragmentSource = fragmentSource;
    }

    /** Compiles + links on first use. Returns false (once) if anything fails. */
    public boolean ensureCompiled() {
        if (attempted) {
            return ok;
        }
        attempted = true;
        try {
            int frag = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(frag, fragmentSource);
            glCompileShader(frag);
            if (glGetShaderi(frag, GL_COMPILE_STATUS) == 0) {
                System.out.println("[LionClient] Outline shader failed to compile: "
                    + glGetShaderInfoLog(frag, 4096));
                return false;
            }
            int prog = glCreateProgram();
            glAttachShader(prog, frag);
            glLinkProgram(prog);
            if (glGetProgrami(prog, GL_LINK_STATUS) == 0) {
                System.out.println("[LionClient] Outline shader failed to link.");
                return false;
            }
            program = prog;
            ok = true;
        } catch (Throwable t) {
            System.out.println("[LionClient] Outline shader error: " + t.getMessage());
            ok = false;
        }
        return ok;
    }

    public void use() {
        glUseProgram(program);
    }

    public static void stop() {
        glUseProgram(0);
    }

    public int uniform(String name) {
        return glGetUniformLocation(program, name);
    }

    public int getProgram() {
        return program;
    }
}

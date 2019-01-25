package com.movlad.semviz.core.graphics.engine;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.movlad.semviz.core.graphics.ShaderProgram;
import com.movlad.semviz.core.graphics.VertexArrayObject;
import com.movlad.semviz.core.graphics.VertexBufferObject;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;

/**
 * Renders a scene with a camera on a canvas.
 */
public class Renderer implements GLEventListener {

    protected Scene scene;
    protected Camera camera;

    protected GL3 gl;
    protected ShaderProgram program;

    private final List<Renderable> renderables;

    public Renderer(Scene scene, Camera camera) {
        this.renderables = new ArrayList<>();
        this.scene = scene;
        this.camera = camera;
    }

    @Override
    public final void init(GLAutoDrawable drawable) {
        gl = (GL3) drawable.getGL();

        gl.glEnable(GL3.GL_DEPTH_TEST);

        try {
            program = new ShaderProgram(gl, this.getClass().getClassLoader()
                    .getResourceAsStream("shaders/shader.glsl"));
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        program.use();
    }

    @Override
    public final void display(GLAutoDrawable drawable) {
        gl.glClear(GL3.GL_DEPTH_BUFFER_BIT | GL3.GL_COLOR_BUFFER_BIT);
        gl.glClearColor(0.027f, 0.184f, 0.372f, 1.0f);

        program.use();
        resetVertexArrays();
        initVertexArrays(scene);
        render();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        resetVertexArrays();
        program.delete();
    }

    @Override
    public final void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        // updating camera's projection matrix based on changes in windows size

        if (camera instanceof PerspectiveCamera) {
            ((PerspectiveCamera) camera).setAspect(width / height);
        } else if (camera instanceof OrthographicCamera) {
            ((OrthographicCamera) camera).setLeft(-width);
            ((OrthographicCamera) camera).setRight(width);
            ((OrthographicCamera) camera).setBottom(-height);
            ((OrthographicCamera) camera).setTop(height);
        }

        camera.updateProjectionMatrix();
    }

    /**
     * Draws renderables on the screen.
     */
    private void render() {
        renderables.stream().filter((renderable) -> (renderable.getObject().isVisible())).filter((renderable) -> (renderable.getObject() instanceof Geometry))
                .forEachOrdered((renderable) -> {
                    Geometry geometry = (Geometry) renderable.getObject();

                    renderable.getVertexArrayObject().bind();

                    setMatrixUniforms(renderable);

                    gl.glDrawArrays(geometry.getDrawingMode(), 0, geometry.getVertexCount());
                });
    }

    /**
     * Sets the matrix uniforms for the currently drawn renderable.
     *
     * @param renderable is the currently draw renderable
     */
    private void setMatrixUniforms(Renderable renderable) {
        int modelUniformLocation = gl.glGetUniformLocation(program.getId(), "model");
        int viewUniformLocation = gl.glGetUniformLocation(program.getId(), "view");
        int projectionUniformLocation = gl.glGetUniformLocation(program.getId(), "projection");

        FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);

        renderable.getObject().getMatrixWorld().get(modelBuffer);
        camera.getMatrixWorld().get(viewBuffer);
        camera.getProjectionMatrix().get(projectionBuffer);

        gl.glUniformMatrix4fv(modelUniformLocation, 1, false, modelBuffer);
        gl.glUniformMatrix4fv(viewUniformLocation, 1, false, viewBuffer);
        gl.glUniformMatrix4fv(projectionUniformLocation, 1, false, projectionBuffer);
    }

    /**
     * Initializes the vertex arrays for the drawable geometries.
     *
     * @param object is the object whose children will be initialized with
     * vertex array objects
     */
    private void initVertexArrays(Object3d object) {
        for (Object3d child : object) {
            if (child instanceof Geometry) {
                Geometry geometry = (Geometry) child;

                VertexArrayObject vertexArrayObject = new VertexArrayObject(gl);

                vertexArrayObject.bind();

                Buffer dataBuffer = FloatBuffer.wrap(geometry.getData());
                VertexBufferObject vertexBufferObject = new VertexBufferObject(gl, dataBuffer, dataBuffer.capacity()
                        * Float.BYTES, GL3.GL_STATIC_DRAW);

                vertexArrayObject.addBuffer(vertexBufferObject, geometry.getLayout());

                renderables.add(new Renderable(child, vertexArrayObject));
            }

            initVertexArrays(child);
        }
    }

    /**
     * Binds vertex array to 0, deletes vertex arrays and reinitializes the
     * {@code renderables} array list
     */
    private void resetVertexArrays() {
        gl.glBindVertexArray(0);

        renderables.forEach((renderable) -> {
            renderable.getVertexArrayObject().delete();
        });

        renderables.clear();
    }

}
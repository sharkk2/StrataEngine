package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.Engine;

import static org.lwjgl.opengl.GL33.*;

public abstract class RenderPass {
   protected final String name;
   protected final Engine engine;

   private static final int QUERY_RING_SIZE = 4;
   private float gpuTime = 0;
   private float cpuTime = 0;
   private final int[] queries = new int[QUERY_RING_SIZE];
   private final boolean[] pending = new boolean[QUERY_RING_SIZE];
   private int writeIndex = 0;

   protected RenderPass(Engine engine, String name) {
      this.name = name;
      this.engine = engine;
      for (int i = 0; i < QUERY_RING_SIZE; i++) {
         queries[i] = glGenQueries();
      }
   }

   protected abstract void onPass(FrameContext frameContext);
   protected abstract void onDestroy();
   protected abstract void onReset();
   protected abstract String[] dependencies();

   public void pass(FrameContext fc) {
      for (int i = 0; i < QUERY_RING_SIZE; i++) {
         int slot = (writeIndex + 1 + i) % QUERY_RING_SIZE;
         if (!pending[slot]) continue;
         if (glGetQueryObjecti(queries[slot], GL_QUERY_RESULT_AVAILABLE) != 0) {
            long nanos = glGetQueryObjecti64(queries[slot], GL_QUERY_RESULT);
            gpuTime = (float) (nanos / 1_000_000.0);
            pending[slot] = false;
         }
      }

      writeIndex = (writeIndex + 1) % QUERY_RING_SIZE;

      if (pending[writeIndex]) {
         long cpuStart = System.nanoTime();
         onPass(fc);
         cpuTime = (float) ((System.nanoTime() - cpuStart) / 1_000_000.0);
         return;
      }

      long cpuStart = System.nanoTime();
      glBeginQuery(GL_TIME_ELAPSED, queries[writeIndex]);
      onPass(fc);
      glEndQuery(GL_TIME_ELAPSED);
      cpuTime = (float) ((System.nanoTime() - cpuStart) / 1_000_000.0);

      pending[writeIndex] = true;
   }

   public void destroy() {
      for (int i = 0; i < QUERY_RING_SIZE; i++) {
         glDeleteQueries(queries[i]);
      }
      onDestroy();
   }

   public void reset() {onReset();}
   public float queryGpuTime() {return gpuTime;}
   public float queryCpuTime() {return cpuTime;}
   public String getName() {return name;}
}
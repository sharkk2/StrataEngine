package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.Engine;


public abstract class RenderPass {
   protected final String name;
   protected final Engine engine;
   private float passTime = 0;

   protected RenderPass(Engine engine, String name)  {
       this.name = name;
       this.engine = engine;
   }

   protected abstract void onPass(FrameContext frameContext);
   protected abstract void onDestroy();
   protected abstract void onReset();
   protected abstract String[] dependencies();

   public void pass(FrameContext fc) {onPass(fc);}
   public void destroy() {onDestroy();}
   public void reset() {onReset();}

   public float queryPassTime() {return passTime;}
   public void updatePassTime(float timeMS) {this.passTime = timeMS;}
}

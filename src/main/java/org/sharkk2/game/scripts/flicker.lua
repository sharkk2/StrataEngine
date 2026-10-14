-- given objects: pointlight, ctx, gameObject, material
-- Component script

timer = ctx.data:getValue("timer")
flickering = ctx.data:getValue("isFlickering")

if timer == nil or flickering == nil then
    ctx.data:setValue("timer", 2.0)
    ctx.data:setValue("isFlickering", false)
    return
end

local scene = ctx:getScene()
local engine = ctx:getEngine()

local dt = engine:getDeltaTime()

if scene:isTagged(gameObject, "BLACKED_OUT") then
    return
end

local currentTimer = timer - dt
if currentTimer <= 0 then
    local nextFlickerState = not flickering
    ctx.data:setValue("isFlickering", nextFlickerState)
    local nextDuration = nextFlickerState and (0.2 + math.random() * 0.6) or (30.0 + math.random() * 90.0)
    ctx.data:setValue("timer", nextDuration)
else 
    ctx.data:setValue("timer", currentTimer)
end

if flickering == true then
  pointlight.intensity = math.random(1, 5)
  material.emissiveStrength = pointlight.intensity * 1.5
else 
  pointlight.intensity = 3;
  material.emissiveStrength = 5;
end
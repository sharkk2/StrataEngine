-- Component script

local engine = gameObject:getEngine()

local enabled = ctx.data:getValue("enabled")
local currentIntensity = ctx.data:getValue("currentIntensity")
local currentFov = ctx.data:getValue("currentFov")
local defaultFov = ctx.data:getValue("defaultFov")
local dirs = ctx.data:getValue("dirs")
local currentOffset = ctx.data:getValue("currentOffset")
local defaultOffset = ctx.data:getValue("defaultOffset")
local flickerTimer = ctx.data:getValue("flickerTimer")
local flickerPulseTimer = ctx.data:getValue("flickerPulseTimer")
local flickerPulsesRemaining = ctx.data:getValue("flickerPulsesRemaining")

if enabled == nil then
    dirs = {}
    enabled = true
    currentIntensity = flashlight.intensity
    defaultFov = cam:getFov()
    currentFov = cam:getFov()
    defaultOffset = mathlib.vec3(flashlight.offset.x, flashlight.offset.y, flashlight.offset.z)
    currentOffset = mathlib.vec3(flashlight.offset.x, flashlight.offset.y, flashlight.offset.z)
    flickerTimer = 10.0 + math.random() * 5.0
    flickerPulseTimer = 0.0
    flickerPulsesRemaining = 0
end

local dt = engine:getDeltaTime()

if engine:getInputService():isKeyPressed(engine:getInputService():getMapping("toggleFlash"):code()) then
    enabled = not enabled
end

local rightClickHeld = engine:getInputService():isMouseBtnDown(engine:getInputService():getMapping("focusFlash"):code())

local coffset = cam:getPositionOffset()
local offsetTarget = rightClickHeld and mathlib.vec3(coffset.x, coffset.y, coffset.z) or defaultOffset
local offsetDiff = offsetTarget:sub(currentOffset)
currentOffset = currentOffset:add(offsetDiff:scale(math.min(1.0, dt * 10.0)))
flashlight.offset:set(currentOffset:raw());

local targetFov = rightClickHeld and (defaultFov - 5.0) or defaultFov
currentFov = currentFov + (targetFov - currentFov) * math.min(1.0, dt * 10.0)
cam:setFov(currentFov)

local frame = engine:getTotalFrameCount()
dirs[frame] = cam:getDirection()

local pastFrame = math.max(frame - 25, 0)
local pastDir = dirs[pastFrame]
if pastDir ~= nil then
    local aimed = mathlib.vec3(pastDir.x, pastDir.y - 0.1, pastDir.z)
    flashlight.spotLightDirection:set(aimed:normalize():raw())
    dirs[pastFrame - 25] = nil
end

local targetIntensity = enabled and 7.0 or 0.0
currentIntensity = currentIntensity + (targetIntensity - currentIntensity) * math.min(1.0, dt * 10.0)

local outputIntensity = currentIntensity

if enabled then
    if flickerPulsesRemaining > 0 then
        flickerPulseTimer = flickerPulseTimer - dt
        if flickerPulseTimer <= 0.0 then
            flickerPulseTimer = 0.03 + math.random() * 0.06
            flickerPulsesRemaining = flickerPulsesRemaining - 1
        end
        if flickerPulsesRemaining % 2 == 0 then
            outputIntensity = 0.0
        end
    else
        flickerTimer = flickerTimer - dt
        if flickerTimer <= 0.0 then
            flickerTimer = 10.0 + math.random() * 5.0
            flickerPulsesRemaining = 1 + math.random(0, 3)
        end
    end
end

flashlight.color = mathlib.vec3(1,1,2):raw()
flashlight.intensity = outputIntensity;

ctx.data:setValue("enabled", enabled)
ctx.data:setValue("currentIntensity", currentIntensity)
ctx.data:setValue("currentFov", currentFov)
ctx.data:setValue("defaultFov", defaultFov)
ctx.data:setValue("dirs", dirs)
ctx.data:setValue("currentOffset", currentOffset)
ctx.data:setValue("defaultOffset", defaultOffset)
ctx.data:setValue("flickerTimer", flickerTimer)
ctx.data:setValue("flickerPulseTimer", flickerPulseTimer)
ctx.data:setValue("flickerPulsesRemaining", flickerPulsesRemaining)
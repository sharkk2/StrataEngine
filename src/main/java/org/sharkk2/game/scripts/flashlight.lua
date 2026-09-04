local engine = gameObject:getEngine()

local enabled = ctx:readState("enabled")
local currentIntensity = ctx:readState("currentIntensity")
local currentFov = ctx:readState("currentFov")
local defaultFov = ctx:readState("defaultFov")
local dirs = ctx:readState("dirs")
local currentOffset = ctx:readState("currentOffset")
local defaultOffset = ctx:readState("defaultOffset")
local flickerTimer = ctx:readState("flickerTimer")
local flickerPulseTimer = ctx:readState("flickerPulseTimer")
local flickerPulsesRemaining = ctx:readState("flickerPulsesRemaining")

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

local rightClickHeld = engine:getInputService():isMouseDown(engine:getInputService():getMapping("focusFlash"):code())

local offsetTarget = rightClickHeld and mathlib.vec3(0, 0, 0) or defaultOffset
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

flashlight.intensity = outputIntensity
flashlight.color = mathlib.vec3(1,1,4):raw()


ctx:state("enabled", enabled)
ctx:state("currentIntensity", currentIntensity)
ctx:state("currentFov", currentFov)
ctx:state("defaultFov", defaultFov)
ctx:state("dirs", dirs)
ctx:state("currentOffset", currentOffset)
ctx:state("defaultOffset", defaultOffset)
ctx:state("flickerTimer", flickerTimer)
ctx:state("flickerPulseTimer", flickerPulseTimer)
ctx:state("flickerPulsesRemaining", flickerPulsesRemaining)
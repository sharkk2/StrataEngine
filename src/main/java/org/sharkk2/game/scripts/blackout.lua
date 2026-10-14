-- Scene script

local timer = scene.values:getValue("timer")
local blackedOut = scene.values:getValue("blacked_out")
local lastBlackout = scene.values:getValue("last_blackout")
if timer == nil then
    scene.values:setValue("timer", 20)
    scene.values:setValue("blacked_out", false)
    scene.values:setValue("last_blackout", 0)
    return
end

if not blackedOut then
    timer = timer - engine:getDeltaTime()
    scene.values:setValue("timer", timer)
    scene.environment.fog.color = scene.environment.globalLight.ambient
    scene.environment.fog.density = 0.005
    scene.environment.fog.blendSkyColor = true
    if timer <= 0 then
        scene.values:setValue("blacked_out", true)
        scene.values:setValue("last_blackout", engine:getCurrentTime())
        local lights = scene:getTagged("BR_LIGHT")
        for i = 0, lights:size() - 1 do
            local v = lights:get(i)
            local light = v:getComponent("LightComponent")
            local model = v:getComponent("ModelComponent")
            model.material.emissiveStrength = 0.002
            light.intensity = 0
            scene:tagObject(v, "BLACKED_OUT")
        end
    end
else
    scene.environment.fog.density = 0.05
    scene.environment.fog.color:set(0.001, 0.001, 0.001)
    scene.environment.fog.blendSkyColor = false
    if (engine:getCurrentTime() - lastBlackout) / 1000 > 6 then
        scene.values:setValue("blacked_out", false)
        scene.values:setValue("timer", math.random(60, 240))
        local lights = scene:getTagged("BR_LIGHT")
        for i = 0, lights:size() - 1 do
            local v = lights:get(i)
            local light = v:getComponent("LightComponent")
            local model = v:getComponent("ModelComponent")
            model.material.emissiveStrength = 3
            light.intensity = 3;
            scene:untagObject(v, "BLACKED_OUT")
        end

    end
end

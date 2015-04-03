# version 110

varying vec3 normal;
varying vec3 position;
varying vec4 color;
varying float blockId;

uniform sampler2D texSampler;
uniform int animTime;
uniform int enableWave;

attribute vec4 positionAttr;
attribute vec2 lightingAndAlphaAttr;
attribute vec3 normalAttr;
attribute float blockIdAttr;

void main() {
    blockId = blockIdAttr;
    color = vec4(vec3(lightingAndAlphaAttr.x), lightingAndAlphaAttr.y);
    position = positionAttr.xyz;
    if(enableWave > 0) {
        vec4 pos = positionAttr;

        float waveLength = 1.0;
        float waveTime = 0.002;
        float waveHeight = 0.1;

        pos.y -= 0.2;

        float posYbuf = pos.z / waveLength + float(animTime) * waveTime * waveLength;

        pos.y -= sin(posYbuf) * waveHeight + sin(posYbuf / 7.0) * waveHeight;
        gl_Position = gl_ModelViewProjectionMatrix * pos;
    } else {
        gl_Position = gl_ModelViewProjectionMatrix*positionAttr;
    }
    normal = normalAttr;
}
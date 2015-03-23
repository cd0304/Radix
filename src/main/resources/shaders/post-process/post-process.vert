attribute vec3 position;
attribute vec2 texCoord;

varying vec3 vPosition;
varying vec2 vTexCoord;

uniform sampler2D texture;

void main() {
    vPosition = position;
    vTexCoord = texCoord;

    gl_Position = gl_ModelViewProjectionMatrix*vec4(position, 1.0);
}
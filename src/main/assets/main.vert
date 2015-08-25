attribute vec2 vPosition;
attribute vec2 vTexCoord;
uniform float fAngle;
varying vec2 texCoord;

void main() {
  texCoord = vTexCoord;

  mat2 rotMatrix = mat2(cos(fAngle), -sin(fAngle),
                        sin(fAngle), cos(fAngle));

  vec2 pos = vPosition * rotMatrix;

  gl_Position = vec4(pos, 0.0, 1.0);
}

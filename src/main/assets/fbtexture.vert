attribute vec2 vPosition;
attribute vec2 vTexCoord;
varying vec2 texCoord;

void main() {
  texCoord = vec2(1.0, 1.0) - vTexCoord;
  vec2 pos = vPosition * vec2(-1.0, 1.0);

  gl_Position = vec4(pos, 0.0, 1.0);
}

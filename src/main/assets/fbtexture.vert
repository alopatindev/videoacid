attribute vec2 vPosition;
attribute vec2 vTexCoord;
varying vec2 texCoord;

void main() {
  //texCoord = vec2(1.0, 1.0) - vTexCoord;
  texCoord = vTexCoord;
  //vec2 mirroredPos = vPosition * vec2(-1.0, 1.0);
  //vec2 aspectAlteredPos = mirroredPos * vec2(1.0, 1.6);
  //gl_Position = vec4(aspectAlteredPos, 0.0, 1.0);
  gl_Position = vec4(vPosition, 0.0, 1.0);
}

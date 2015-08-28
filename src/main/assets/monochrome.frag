#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;

uniform vec3 vOtherColor;
uniform float fLow;
uniform float fHigh;

void main() {
  vec4 tex = texture2D(sTexture, texCoord);
  vec3 otherColor = vOtherColor;

  float bw = (tex.r + tex.g + tex.b) * 0.33;
  float low = fLow;
  float high = fHigh;
  float alpha = 0.5;

  if (bw < low || bw > high) {
    discard;
  }

  gl_FragColor = vec4(otherColor, alpha);
}

#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;

uniform vec3 vOtherColor;
uniform float fLow;
uniform float fHigh;
uniform float fMadness;

void main() {
  vec4 tex = texture2D(sTexture, texCoord);
  vec3 otherColor = vOtherColor;

  float bw = (tex.r + tex.g + tex.b) * 0.33;
  float low = fLow;
  float high = fHigh;
  //float alpha = 0.2 * fMadness;
  //float alpha = clamp(2.0 * fMadness, 0.0, 0.2);
  float alpha = 0.2;
  //float alpha = clamp((1.0 - fMadness) * 0.6, 0.0, 0.6);

  if (bw < low || bw > high) {
    discard;
  }

  gl_FragColor = vec4(otherColor, alpha);
}

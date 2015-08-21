#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;

uniform vec3 vOtherColor;
uniform float fLow;
uniform float fHigh;
uniform float fInvertedMaskSign;

void main() {
  vec4 tex = texture2D(sTexture, texCoord);
  vec3 otherColor = vOtherColor;

  float bw = (tex.r + tex.g + tex.b) * 0.33;
  float low = fLow;
  float high = fHigh;
  float invertedMaskSign = fInvertedMaskSign;

  float rawMask = clamp(bw, low, high) - low;
  float mask = sign(rawMask * invertedMaskSign);
  vec3 mixed = mask * otherColor;
  float alpha = bw * 0.3;

  gl_FragColor = vec4(mixed, alpha);
}

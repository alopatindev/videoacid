#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform float fAlpha;

void main() {
  vec4 outTex = texture2D(sTexture, texCoord);
  outTex.a *= fAlpha;
  gl_FragColor = outTex;
}

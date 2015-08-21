#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec3 vOtherColor;

void main() {
  vec4 tex = texture2D(sTexture, texCoord);
  //vec3 otherColor = vec3(0.6, 0.4, 0.2);
  //vec3 otherColor = vec3(1.0, 0.0, 0.0);
  vec3 otherColor = vOtherColor;

  float bw = (tex.r + tex.g + tex.b) / 3.0;
  float low = 0.5;
  float high = 0.8;

  float mask = sign(clamp(bw, low, high) - low);
  vec3 mixed = mask * otherColor;

  gl_FragColor = vec4(mixed, bw);
}

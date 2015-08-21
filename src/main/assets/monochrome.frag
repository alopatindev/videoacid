#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;

void main() {
  vec4 tex = texture2D(sTexture, texCoord);
  vec3 otherColor = vec3(0.6, 0.4, 0.2);

  float bw = (tex.r + tex.g + tex.b) / 3.0;
  float bwFiltered = sign(0.5 - clamp(bw, 0.5, 1.0));
  //vec3 bf = tex.rgb - bwFiltered;

  //if (bwFiltered > 0.7) bf = otherColor;

  //gl_FragColor = vec4(bf, 1.0 - bw);
  gl_FragColor = vec4(bwFiltered * otherColor, bw);

  //gl_FragColor = tex;
  //gl_FragColor = vec4(bww, bww, bww, 1.0);
  //gl_FragColor = vec4(tex.r, tex.g, tex.b, 0.5);
}

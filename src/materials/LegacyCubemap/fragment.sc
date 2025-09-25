$input v_texcoord0, v_fogColor, v_worldPos, v_underwaterRainTime

#include <bgfx_shader.sh>
#include <newb/main.sh>

uniform vec4 FogAndDistanceControl;
uniform vec4 FogColor;
uniform vec4 ViewPositionAndTime;

SAMPLER2D_AUTOREG(s_MatTexture);

// obsolete now
// could be used for clouds, aurora?

void main() {
  vec3 viewDir = normalize(v_worldPos);

  nl_environment env;
  env.end = false;
  env.nether = false;
  env.underwater = v_underwaterRainTime.x > 0.5;
  env.rainFactor = v_underwaterRainTime.y;

  nl_skycolor skycol = nlOverworldSkyColors(env.rainFactor, FogColor.rgb);
  
  vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
  
if(FogAndDistanceControl.x != 0.0){
  #if NL_CLOUD_TYPE == 4
    vec3 view2 = viewDir;
    view2.y = pow(view2.y, 0.7);
    vec3 wpos = view2/abs(view2.y);
    float fade = smoothstep(50.0, 0.0,length(wpos.xz)) * smoothstep(0.0, 0.4,  view2.y);
    diffuse = VLClouds(normalize(viewDir.xyz), FogAndDistanceControl, FogColor, ViewPositionAndTime.w, skycol.horizon, skycol.zenith);
    diffuse.a *= fade;
    if(viewDir.y < 0.0) discard;
  #endif
}
  diffuse.rgb = colorCorrection(diffuse.rgb);
  gl_FragColor = diffuse;
}

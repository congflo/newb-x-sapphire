$input v_color0, v_color1, v_fog, v_refl, v_texcoord0, v_lightmapUV, v_extra, v_worldPos, v_position,  v_color2, v_cloudPos

#include <bgfx_shader.sh>
#include <newb/main.sh>

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

uniform vec4 RenderChunkFogAlpha;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;

float fog_fade(vec3 wPos) {
  return clamp(2.3-length(wPos*vec3(0.005, 0.002, 0.005)), 0.0, 1.0);
}

float getHeightFromTex(vec2 uv, sampler2D tex) {
	vec3 t = texture2D(tex, uv).rgb;
return (t.x+t.y+t.z)/3.0;
}

vec4 getNormalMapFromTex(vec2 uv, vec2 resolution, float scale, sampler2D tex) {
  vec2 step = 1.0 / resolution;

  float height = getHeightFromTex(uv, tex);

  vec2 dxy = height - vec2(
      getHeightFromTex(uv + vec2(step.x, 0.0), tex),
      getHeightFromTex(uv + vec2(0.0, step.y), tex)
  );
  return vec4(normalize(vec3(dxy * scale / step, 1.0)), height);
}

void main() {
  #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING)
    gl_FragColor = vec4(1.0,1.0,1.0,1.0);
    return;
  #endif

float fogfade = fog_fade(v_worldPos.xyz);

vec2 uvl = v_lightmapUV;
nl_environment env = nlDetectEnvironment(FogColor.rgb, FogAndDistanceControl.xyz);
nl_skycolor skycol = nlSkyColors(env, FogColor.rgb);

float rain = detectRain(FogAndDistanceControl.xyz);
float time = ViewPositionAndTime.w;
  vec3 viewDir = normalize(v_worldPos);
 // viewDir.y = -viewDir.y;
  float day = pow(max(min(1.0 - FogColor.r * 1.2, 1.0), 0.0), 0.4);
  float night = pow(max(min(1.0 - FogColor.r * 1.5, 1.0), 0.0), 1.2);
  float dusk = max(FogColor.r - FogColor.b, 0.0);
  float cave = smoothstep(0.5,0.0, uvl.y); 
  
  vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
  vec4 color = v_color0;

  #ifdef ALPHA_TEST
    if (diffuse.a < 0.6) {
      discard;
    }
  #endif

  #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
    diffuse.rgb *= mix(vec3(1.0,1.0,1.0), texture2D(s_SeasonsTexture, v_color1.xy).rgb * 2.0, v_color1.z);
  #endif
  
  vec2 shadowstep = v_extra.b > 0.9 ? vec2(0.815, 0.795) : vec2(0.875, 0.855);
  float shadowmap = smoothstep(shadowstep.x, shadowstep.y, pow(uvl.y,2.0));
  
highp vec3 normal = normalize(cross(dFdx(v_position),dFdy(v_position)));

vec3 shift = diffuse.rgb;
const vec2 oc = vec2(1.0, -1.0)*0.00175;
vec3 normalmap = shift;
shift -= texture2D(s_MatTexture, v_texcoord0 + oc*0.13).rgb;
shift += texture2D(s_MatTexture, v_texcoord0 - oc*0.23).rgb;
shift += pow(shift, vec3_splat(1.5));
normalmap = clamp(diffuse.rgb*0.8 + (diffuse.rgb*shift)*0.2, 0.0, 1.0);

#ifdef NORMALMAP
diffuse.rgb = normalmap;
#endif
  

  vec3 glow = nlGlow(s_MatTexture, v_texcoord0, v_extra.a);

  diffuse.rgb *= diffuse.rgb;

  vec3 lightTint = texture2D(s_LightMapTexture, v_lightmapUV).rgb;
  lightTint = mix(lightTint.bbb, lightTint*lightTint, 0.35 + 0.65*v_lightmapUV.y*v_lightmapUV.y*v_lightmapUV.y);

  color.rgb *= lightTint;
 
  #if defined(TRANSPARENT) && !(defined(SEASONS) || defined(RENDER_AS_BILLBOARDS))
    if (v_extra.b > 0.9) {
      diffuse.rgb = vec3_splat(1.0 - NL_WATER_TEX_OPACITY*(1.0 - diffuse.b*1.8));
      diffuse.a = color.a;
    }
  #else
    diffuse.a = 1.0;
  #endif
  if(v_extra.b > 0.9){
 
 vec3 skycolor = nlRenderSky(skycol, env, viewDir, FogColor.rgb, ViewPositionAndTime.w);

    float specular = smoothstep(SUN_REFL, 0.0, abs(viewDir.z));
    specular *= specular*smoothstep(0.6,1.0,abs(viewDir.x));
    specular *= specular;
    specular += specular*specular*specular*specular;
    
    specular *= max(FogColor.r-FogColor.b, 0.0);
    specular *= max(0.0,1.0-cave);
    vec3 sunrefl = 4.0*mix(skycol.horizonEdge, skycol.zenith, 0.5) * specular * specular * specular;
    sunrefl += sunrefl;
    
  color.rgb = mix(skycolor,v_refl.rgb, 1.0);
    viewDir.y = -viewDir.y;
#ifdef NL_SHOOTING_STAR
    color.rgb += 8.0*v_refl.rgb*NL_SHOOTING_STAR*nlRenderShootingStar(viewDir, FogColor.rgb, ViewPositionAndTime.w)*(1.0-shadowmap);
#endif
#ifdef NL_GALAXY_STARS
    color.rgb += 8.0*v_refl.rgb*NL_GALAXY_STARS*nlRenderGalaxy(viewDir, FogColor.rgb, env, ViewPositionAndTime.w)*max(0.0,1.0)*night*(1.0-shadowmap);
#endif
  if(!env.end || !env.underwater){
  color.rgb = mix(color.rgb,v_color0.rgb, cave)*mix(vec3_splat(1.0), texture2D(s_LightMapTexture, v_lightmapUV).xyz, cave);
  }
  color.rgb += sunrefl*v_refl.rgb*(1.0-cave);
  }
  diffuse.rgb *= color.rgb;
  
  #if !(defined(ALPHA_TEST) || defined(RENDER_AS_BILLBOARDS) || defined(SEASONS))
  diffuse.rgb += glow;
  #endif
 
    
 vec3 torchColor;
   if (env.underwater) {
    torchColor = NL_UNDERWATER_TORCH_COL;
  } else if (env.end) {
    torchColor = NL_END_TORCH_COL;
  } else if (env.nether) {
    torchColor = NL_NETHER_TORCH_COL;
  } else {
    torchColor = NL_OVERWORLD_TORCH_COL;
  }
  
  if(!env.underwater){
  shadowmap *= 1.0-env.rainFactor;
  }
  shadowmap *= 1.0-uvl.x;
  shadowmap *= 1.0 - 0.5*night*(1.0-cave);
if ((!env.nether && !env.end) || !gl_FrontFacing) {
#if NL_CLOUD_TYPE != 0
  shadowmap += 0.85*abs(normal.x);
#endif
}
  diffuse.rgb *= 1.0-0.3*shadowmap;


  




  if (v_extra.b > 0.9) {
  // old code for later use
  
  /*  diffuse.rgb += v_refl.rgb*v_refl.a;
    diffuse.rgb += sunrefl*v_refl.a;
  */
    float waterlight = pow(v_lightmapUV.x * 1.2, 7.0);
    waterlight *= mix(1.0,0.0,1.0-cave*mix(mix(1.0, 0.0, night), 0.0, env.rainFactor));
    diffuse.rgb += torchColor*waterlight;
    
  } else if (v_refl.a > 0.0) {
    // reflective effect - only on xz plane
    float dy = abs(dFdy(v_extra.g));
    if (dy < 0.0002) {
      float mask = v_refl.a*(clamp(v_extra.r*10.0,8.2,8.8)-7.8);
      diffuse.rgb *= 1.0 - 0.6*mask;
      diffuse.rgb += v_refl.rgb*mask;
    }
  }
  
 vec4 fogColor = v_fog;
    vec3 modelCamPos = ViewPositionAndTime.xyz - v_worldPos.xyz;
    float camDis = length(modelCamPos);
    float relativeDist = camDis / FogAndDistanceControl.z;

  #ifdef NL_GODRAY
    float godray = NL_GODRAY*nlRenderGodRayIntensity(v_position.xyz, v_worldPos.xyz, ViewPositionAndTime.w, v_lightmapUV, relativeDist, fogColor.rgb, FogAndDistanceControl.xy); 
    diffuse.rgb += NL_GODRAY*fogColor.rgb * godray;
    //fogColor.a = mix(fogColor.a, 1.0, godray);
  #endif

  diffuse.rgb = mix(diffuse.rgb, fogColor.rgb, fogColor.a);
     
  diffuse.rgb = colorCorrection(diffuse.rgb);

  gl_FragColor = diffuse;
}

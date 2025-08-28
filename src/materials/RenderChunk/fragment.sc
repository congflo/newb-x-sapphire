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

vec3 nlSky(nl_skycolor skycol, nl_environment env, vec3 viewDir, vec3 FOG_COLOR, float t) {
  vec3 sky;
  
  //viewDir.y = -viewDir.y;
  float rain = detectRain(FogAndDistanceControl.xyz);
  
  
/*Shooting star*/
#ifdef NL_SHOOTING_STAR
  vec3 star = nlRenderShootingStar(viewDir, FogColor.rgb, ViewPositionAndTime.w);
  star = NL_SHOOTING_STAR*star;
#endif
  
/*galaxy*/  
#ifdef NL_GALAXY_STARS
vec3 galaxy = nlRenderGalaxy(viewDir, FogColor.rgb, env, t);
galaxy = NL_GALAXY_STARS*galaxy;
#endif

/*sun reflection*/

      vec3 sunbloom = getSunBloom(viewDir.x, skycol.horizonEdge, FogColor.rgb);
      float sunbl = (sunbloom.r + sunbloom.g + sunbloom.b)/3.0;
      //sunbloom += vec3(0.13,0.11,0.12)*2.0*sunbl+vec3(0.13,0.11,0.12)*smoothstep(0.85,1.0,sunbl) /*+ vec3(0.13,0.11,0.12)*1.25*smoothstep(0.975,1.0,sunbl)*/; 
      

  if (env.end) {
    viewDir.xz = -viewDir.xz;
    sky = renderEndSky(skycol.horizon, skycol.zenith, viewDir, t);
    #ifdef NL_GALAXY_STARS
    sky += galaxy;
    #endif
  } else {
    sky = renderOverworldSky(skycol, viewDir);
    
    //sky = mix(sky, sunbloom, sunbl);
    sky += sunbloom;
    #ifdef NL_GALAXY_STARS
    sky += galaxy;
    #endif
    #ifdef NL_SHOOTING_STAR
    sky += star;
    #endif
    
    
    
    #ifdef NL_RAINBOW
      sky += mix(NL_RAINBOW_CLEAR, NL_RAINBOW_RAIN, env.rainFactor)*spectrum((viewDir.z+0.6)*8.0)*max(viewDir.y, 0.0)*FOG_COLOR.g;
    #endif
    #ifdef NL_UNDERWATER_STREAKS
      if (env.underwater) {
        float a = atan2(viewDir.x, viewDir.z);
        float grad = 0.5 + 0.5*viewDir.y;
        grad *= grad;
        float spread = (0.5 + 0.5*sin(3.0*a + 0.2*t + 2.0*sin(5.0*a - 0.4*t)));
        spread *= (0.5 + 0.5*sin(3.0*a - sin(0.5*t)))*grad;
        spread += (1.0-spread)*grad;
        float streaks = spread*spread;
        streaks *= streaks;
        streaks = (spread + 3.0*grad*grad + 4.0*streaks*streaks);
        sky += 2.0*streaks*skycol.horizon;
      } 
    #endif
    
  }

  return sky;
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
  viewDir.y = -viewDir.y;
  
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
  
  float shadowmap = smoothstep(0.875, 0.855, pow(uvl.y,2.0));
  shadowmap *= mix(1.0,0.0,env.rainFactor);
  shadowmap *= mix(1.0,0.0,uvl.x);
  shadowmap *= mix(1.0, 0.5, night);
  diffuse.rgb *= 1.0-0.3*shadowmap;
  
vec3 normal = normalize(cross(dFdx(v_position),dFdy(v_position)));
float dirfac = 0.25;
  dirfac *= mix(1.0, 0.0, smoothstep(0.875, 0.855, pow(uvl.y,2.0)));
  dirfac *= mix(1.0,0.0, uvl.x);
if (!env.underwater) {
  dirfac *= mix(1.0, 0.0, env.rainFactor);
}

#if NL_CLOUD_TYPE == 0
dirfac *= 0.0;
#endif
if (!env.nether && !env.end) {
diffuse.rgb *= 1.0-dirfac*abs(normal.x);
}

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

  diffuse.rgb *= color.rgb;
  
  #ifndef ALPHA_TEST
  diffuse.rgb += glow;
  #endif
   
    float specular = smoothstep(SUN_REFL, 0.0, abs(viewDir.z));
    specular *= specular*smoothstep(0.6,1.0,abs(viewDir.x));
    specular *= specular;
    specular += specular*specular*specular*specular;
    
    specular *= max(FogColor.r-FogColor.b, 0.0);
    specular *= max(0.0,1.0)*(1.0-cave);
    vec3 sunrefl = 4.0*skycol.horizonEdge * specular * specular * specular;
    sunrefl += sunrefl;
    
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
  
 float watershadow = smoothstep(0.800, 0.760, pow(uvl.y,2.0));
 vec3 skycolor = nlRenderSky(skycol, env, viewDir, FogColor.rgb, ViewPositionAndTime.w);
 
 float wavepower;
 if(env.end){
 wavepower = 0.0;
 }else {
 wavepower = 1.5;
 }
vec3 waternormal = getWaterNormalMapFromHeight(v_position.xz*vec2(1.0,-1.0)+0.2*time, vec2(-4.0,2.0), wavepower, 0.2*time).xyz;
//vec3 viewD = reflect(viewDir, waternormal); //sky viewDir
 //viewD.z = -viewD.z;
 
 //delete later
 vec3 viewD = viewDir;
 
vec3 skyrefl = nlSky(skycol, env, viewD, FogColor.rgb, ViewPositionAndTime.w);
 float specular2 = smoothstep(0.8, 0.0, abs(viewD.z));
    specular2 *= specular2*smoothstep(0.6,1.0,abs(viewD.x));
    specular2 *= specular2;
    specular2 += specular2*specular2*specular2*specular2;
    
    specular2 *= max(FogColor.r-FogColor.b, 0.0);
    vec3 sunrefl2 = 5.0*skycol.horizonEdge * specular2 * specular2;
    sunrefl2 += sunrefl2;
    
vec3 wetReflrgb = getSkyRefl(skycol, env, viewD, FogColor.rgb, time);
wetReflrgb *= mix(wetReflrgb,wetReflrgb*0.0, cave);

 /* Volumetric Cloud */
 viewD.xz *= 0.3 + v_color2.w; 

  float a = 0.8; // or -ve
     float cosa = cos(a); float sina = sin(a);

      vec2 p = 3.0*viewD.xz/(0.08 + 0.25*abs(viewD.y));
           p = mul(p, mtxFromRows(vec2(cosa, sina), vec2(-sina, cosa)));
      vec4 clouds = renderClouds(p, ViewPositionAndTime.w, rain, skycol.horizonEdge, skycol.zenith, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, NL_CLOUD3_SHADOW);
          float b = 0.8;
          float cosb = cos(b); float sinb = sin(b);
          
          vec2 pc = 3.0*viewD.xz/(0.01 + 0.5*abs(viewD.y));
           pc = mul(pc, mtxFromRows(vec2(cosb, sinb), vec2(-sinb, cosb)));
      vec4 cirrus = renderCloudCirrus(pc, ViewPositionAndTime.w, rain, skycol.horizonEdge, skycol.zenith, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, 0.1);
      clouds = mix(cirrus, clouds, 0.3+0.8*clouds.a);
    
          #ifdef NL_AURORA
        p.xy *= 34.7;
        clouds += renderAurora(p.xyy, ViewPositionAndTime.w, rain, FogColor.rgb)*(1.0-0.7*clouds.a);
      #endif

      clouds.a *= smoothstep (0.0,0.4, viewD.y);
      
    #if NL_CLOUD_TYPE == 3
    if(!env.end){
    skyrefl = mix(skyrefl, clouds.rgb, clouds.a);
    }
    #endif
if(!env.end){
  skyrefl = mix(skyrefl, wetReflrgb*0.7, watershadow);
}
    #undef NL_UNDERWATER_TINT
    #define NL_UNDERWATER_TINT skycolor
    #undef NL_WATER_TINT
    #define NL_WATER_TINT skycolor




  if (v_extra.b > 0.9) {
  #ifdef WATER_REFLECTION

    if(!env.underwater && !env.nether){
       
    diffuse.rgb = skyrefl;
    diffuse.rgb += sunrefl2;
    }
  #else
    diffuse.rgb += v_refl.rgb*v_refl.a;
    diffuse.rgb += sunrefl*v_refl.a;
  #endif
  
    diffuse.rgb += torchColor*pow(v_lightmapUV.x * 1.2, 7.0);
    
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

     if (v_extra.b > 0.9){
     if(!env.underwater){
     #ifdef WATER_REFLECTION
     diffuse.rgb = mix(diffuse.rgb, skyrefl, v_fog.a);
     #endif
     }
     }
     
  diffuse.rgb = colorCorrection(diffuse.rgb);

  gl_FragColor = diffuse;
}

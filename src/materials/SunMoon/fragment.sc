$input v_texcoord0, v_pos, v_uvpos

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/config.h>
  #include <newb/main.sh>


  uniform vec4 SunMoonColor;
  uniform vec4 ViewPositionAndTime;
  uniform vec4 FogColor;
  uniform vec4 FogAndDistanceControl;
  
  SAMPLER2D_AUTOREG(s_SunMoonTexture);
#endif

float sunmoonshape(vec3 pos, vec4 Fog){
  float radius = 500.0*length(pos.xz);
  float round = exp(4.0-radius*0.65);
  float bloom = length(exp(1.0-radius*0.05));
  round += bloom*mix(1.0, 0.0, pow(max(Fog.r - Fog.b, 0.0), 0.5));
  
  return 0.1257*round;
}

vec4 sunmoon(bool isMoon, vec3 pos, vec3 horizon, vec4 Fog){
  float shape = sunmoonshape(pos, Fog);

  vec4 result = vec4_splat(0.0);  
  if(isMoon){
  result = vec4(horizon*8.0, 1.5*shape);
  } else {
  result = vec4(horizon*mix(vec3(1.0,0.75,0.572), vec3_splat(1.0), max(Fog.r - Fog.b, 0.0)), shape);
  }
  return result;
}
  
void main() {
  #ifndef INSTANCING
  
  float day = pow(max(min(1.0 - FogColor.r * 1.2, 1.0), 0.0), 0.4);
  float night = pow(max(min(1.0 - FogColor.r * 1.5, 1.0), 0.0), 1.2);
  float dusk = max(FogColor.r - FogColor.b, 0.0);

    vec4 color = vec4_splat(0.0);
    float t = 0.6*ViewPositionAndTime.w;
    float rain = detectRain(FogAndDistanceControl.xyz);
    float c = atan2(v_pos.x, v_pos.z);
    float g = 1.0-min(length(v_pos*2.0), 1.0);
    g *= g*g*g;
    //g *= 1.2+0.25*sin(c*2.0 - t)*sin(c*5.0 + t);
    //g *= 0.5;
    nl_skycolor skycol = nlOverworldSkyColors(rain, FogColor.rgb);
  
    vec2 uv = v_texcoord0;
    ivec2 ts = textureSize(s_SunMoonTexture, 0);
    bool isMoon = ts.x > ts.y;
    if (isMoon) {
      uv = vec2(0.25,0.5)*(floor(uv*vec2(4.0,2.0)) + 0.5 + 10.0*v_pos.xz);
      color.rgb += g*skycol.horizon*mix(vec3(0.2,0.2,0.2),vec3(0.7,0.7,0.45),pow(night,2.0))*max(0.0, night);
    } else {
      uv = 0.5 + 10.0*v_pos.xz;
      color.rgb += g*skycol.horizon*vec3(1.0,0.85,0.672)*0.1*(1.0-pow(dusk,0.5));
    }

    if (max(abs(v_pos.x),abs(v_pos.z)) < 0.5/10.0) {
      color += texture2D(s_SunMoonTexture, uv);
    }

    color.rgb *= SunMoonColor.rgb;
    color.rgb *= 4.5*color.rgb;
    float tr = 1.0 - SunMoonColor.a;
    color.a = 1.0 - tr*tr*tr;
    color.rgb = colorCorrection(color.rgb);
  #ifdef ROUND_SUNMOON
   vec4 color2 = sunmoon(isMoon, v_pos,  skycol.horizonEdge, FogColor);
   color2.rgb = colorCorrection(color2.rgb);
   gl_FragColor = color2;
  #else
    gl_FragColor = color;
  #endif
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}

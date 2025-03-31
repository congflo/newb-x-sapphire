$input v_texcoord0, v_pos

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/config.h>
  #include <newb/main.sh>
  

  uniform vec4 SunMoonColor;
  uniform vec4 ViewPositionAndTime;
  uniform vec4 FogColor;
  
  SAMPLER2D_AUTOREG(s_SunMoonTexture);
#endif

void main() {
  #ifndef INSTANCING
    vec4 color = vec4_splat(0.0);
    float t = 0.6*ViewPositionAndTime.w;

    float c = atan2(v_pos.x, v_pos.z);
    float g = 1.0-min(length(v_pos*2.0), 1.0);
    g *= g*g*g;
    //g *= 1.2+0.25*sin(c*2.0 - t)*sin(c*5.0 + t);
    //g *= 0.5;
    g *= mix(g, g*0.0,(FogColor.r-FogColor.b, 0.0));
    
    vec2 uv = v_texcoord0;
    ivec2 ts = textureSize(s_SunMoonTexture, 0);
    bool isMoon = ts.x > ts.y;
    if (isMoon) {
      uv = vec2(0.25,0.5)*(floor(uv*vec2(4.0,2.0)) + 0.5 + 10.0*v_pos.xz);
      color.rgb += g*MOON_BLOOM;
    } else {
      uv = 0.5 + 10.0*v_pos.xz;
      color.rgb += g*SUN_BLOOM;
    }

    if (max(abs(v_pos.x),abs(v_pos.z)) < 0.5/10.0) {
      color += texture2D(s_SunMoonTexture, uv);
    }

    color.rgb *= SunMoonColor.rgb;
    color.rgb *= 4.5*color.rgb;
    float tr = 1.0 - SunMoonColor.a;
    color.a = 1.0 - tr*tr*tr;
    color.rgb = colorCorrection(color.rgb);
    gl_FragColor = color;
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}

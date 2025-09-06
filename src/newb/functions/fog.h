#ifndef FOG_H
#define FOG_H

float nlRenderFogFade(float relativeDist, vec3 FOG_COLOR, vec2 FOG_CONTROL) {
  #ifdef NL_FOG
    float fade = smoothstep(FOG_CONTROL.x, FOG_CONTROL.y, relativeDist);

    // misty effect
    float density = NL_MIST_DENSITY*(19.0 - 18.0*FOG_COLOR.g);
    fade += (1.0-fade)*(0.3-0.3*exp(-relativeDist*relativeDist*density));
    bool underwater = detectUnderwater(FOG_COLOR, FOG_CONTROL);
    float opacity = underwater ? 1.0 : NL_FOG;
    return opacity * fade;
  #else
    return 0.0;
  #endif
}

float nlRenderGodRayIntensity(vec3 cPos, vec3 worldPos, float t, vec2 uv1, float relativeDist, vec3 FOG_COLOR, vec2 FOG_CONTROL) {
  float a = -1.6; // or -ve
  float cosa = cos(a); float sina = sin(a); 
  // offset wPos (only works upto 16 blocks)
  vec3 wpos = worldPos;
  worldPos.yz = mul(worldPos.yz, mtxFromRows(vec2(cosa, sina), vec2(-sina, cosa)));
  worldPos.xz *= 0.8;
  // offset wPos (only works upto 16 blocks)
  vec3 offset = cPos - 20.0*fract(worldPos*0.01);
  offset = abs(2.0*fract(offset*0.0625)-1.0);
  offset = offset*offset*(3.0-2.0*offset);
  //offset = 0.5 + 0.5*cos(offset*0.392699082);

  //vec3 ofPos = wPos+offset;
  vec3 nrmof = worldPos/worldPos.y;
  //nrmof *= nrmof/(nrmof+FOG_CONTROL.x);
  float u = nrmof.z/length(nrmof.zy);
  float diff = dot(offset,vec3(0.1,0.2,1.0)) + 0.07*t;
  float mask = nrmof.x*nrmof.x;

  float vol = sin(16.0*u + 1.5*diff)*sin(18.0*u + diff);
  
  
  vol *= vol*mask*uv1.y*(1.0-mask*mask);
  
  vol *= mix(0.9,0.9,smoothstep(0.1,0.9,relativeDist))/relativeDist;
  vol *= mix(0.0,1.0,smoothstep(0.0,1.0,abs(worldPos.x)*relativeDist));
  // dawn/dusk mask
  vol *= mix(0.0,1.0,max(FOG_COLOR.r-FOG_COLOR.b, 0.0));
  vol = clamp(vol,0.0,0.5);
  vol = smoothstep(0.0, 0.5, vol);
  return vol;
}

#endif

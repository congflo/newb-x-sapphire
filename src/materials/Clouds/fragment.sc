$input v_color0, v_color1, v_color2, v_fogColor
#include <newb/config.h>

#include <bgfx_shader.sh>
#include <newb/main.sh>


uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;

  #define V_CLOUD_STEPS 6 //affect performance, recommend 8
  #define V_CLOUD_DETAIL_QUALITY 4 //affect performance 
  #define V_CLOUD_DETAIL 2.8
  #define V_CLOUD_HEIGHT 1.1

float newhash(vec3 p)  // replace this by something better
{
    p  = fract( p*0.3183099+.1 );
	p *= 17.0;
    return fract( p.x*p.y*p.z*(p.x+p.y+mix(p.z-p.x, p.x+p.y, 1.0)));
}

float newnoise( in vec3 x )
{
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);

    return mix(mix(mix( newhash(i+vec3(0,0,0)),
                        newhash(i+vec3(1,0,0)),f.x),
                   mix( newhash(i+vec3(0,1,0)),
                        newhash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix( newhash(i+vec3(0,0,1)),
                        newhash(i+vec3(1,0,1)),f.x),
                   mix( newhash(i+vec3(0,1,1)),
                        newhash(i+vec3(1,1,1)),f.x),f.y),f.z);
}

highp float fbm(vec3 p, float t, float rain) {
  float f = 0.0;
  float amp = 0.5;
  for (int i = 0; i <= V_CLOUD_DETAIL_QUALITY; i++) {
    f += amp * noise2D(p + 0.05*t);
    p *= V_CLOUD_DETAIL;
    amp *= mix(0.45, 0.35, rain);
  }
  return clamp(length(exp(-f)),0.0,1.0) + 0.2/5.0 ;
}


vec4 VLClouds(vec3 viewDir, vec4 FogAndDistanceControl, vec4 FogColor, float time, vec3 horizon, vec3 zenith) {
    time *= 0.15;
    float dusk = max(FogColor.r - FogColor.b, 0.0);
    float cloudBase = 0.9;
    float cloudTop = 1.3;
    int steps = V_CLOUD_STEPS;
    float stepSize = (cloudTop - cloudBase) / float(steps);
    float rain = mix(smoothstep(0.66, 0.3, FogAndDistanceControl.x), 0.0, step(FogAndDistanceControl.x, 0.0));
    vec3 cloudAccum = vec3_splat(0.0);

    float alphaAccum = 0.0;
    float jitter = fract(sin(dot(viewDir.xz, vec2_splat(332.233))) * 87758.5453);
    for (int i = 0; i <= steps ; i++) {
        float height = cloudBase + stepSize * (float(i)+jitter);
        float t = V_CLOUD_HEIGHT*height / max(0.01, abs(0.05+viewDir.y));
        vec3 pos = viewDir * t ;

        vec3 noisePos = vec3(pos.xz + time * 0.05, height*0.85);
        float base = fbm(noisePos, time, rain);

        float heightNorm = (height - cloudBase) / (cloudTop - cloudBase);
        float heightFactor = smoothstep(0.0, 1.0, heightNorm) * (1.0 - smoothstep(0.8, 1.0, heightNorm));
        heightFactor *= smoothstep(0.2, 0.6, base);

        float density = 1.5*clamp(base - 0.55, 0.0, 1.0);
        density = pow(density, 3.0) * heightFactor;

        float alpha = 1.0 - smoothstep(0.015, 0.0055, density);
        alpha *= (1.0 - alphaAccum);

       float scattering = smoothstep(0.0, 0.9, heightNorm);
  float night = pow(max(min(1.0 - FogColor.r * 1.5, 1.0), 0.0), 1.2);
        vec3 cloudColor = mix(0.5*(mix(horizon, zenith, mix(mix(0.8,1.0,dusk), 0.0, night)) +horizon) , mix(horizon, zenith, mix(1.0, 0.8, night)), 1.0-scattering);

        cloudAccum += cloudColor * alpha;
        alphaAccum += alpha;

        if (alphaAccum > 0.98 && viewDir.y < 0.9) break;
    }

      vec4 clouds = vec4(mix(0.5*(mix(horizon, zenith, 0.1)+horizon), cloudAccum, alphaAccum), alphaAccum);
      clouds.rgb *= 1.0-0.1*rain;
      return clouds;
}
    
#define NL_CLOUD_PARAMS(x) NL_CLOUD2##x##STEPS, NL_CLOUD2##x##THICKNESS, NL_CLOUD2##x##RAIN_THICKNESS, NL_CLOUD2##x##VELOCITY, NL_CLOUD2##x##SCALE, NL_CLOUD2##x##DENSITY, NL_CLOUD2##x##SHAPE

void main() {
  vec4 color = v_color0;
  
    float day = pow(max(min(1.0 - FogColor.r * 1.2, 1.0), 0.0), 0.4);
  float night = pow(max(min(1.0 - FogColor.r * 1.5, 1.0), 0.0), 1.2);
  float dusk = max(FogColor.r - FogColor.b, 0.0);

  #if NL_CLOUD_TYPE >= 2
    vec3 vDir = normalize(v_color0.xyz);

    #if NL_CLOUD_TYPE == 2
      color = renderCloudsRounded(vDir, v_color0.xyz, v_color1.w, v_color2.w, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_));

      #ifdef NL_CLOUD2_LAYER2
        vec2 parallax = vDir.xz / abs(vDir.y) * NL_CLOUD2_LAYER2_OFFSET;
        vec3 offsetPos = v_color0.xyz;
        offsetPos.xz += parallax;
        vec4 color2 = renderCloudsRounded(vDir, offsetPos, v_color1.a, v_color2.a*2.0, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_LAYER2_));
        color = mix(color2, color, 0.2 + 0.8*color.a);
        
      #endif

      #ifdef NL_AURORA
        color += renderAurora(v_color0.xyz, v_color2.a, v_color1.a, v_fogColor)*(1.0-0.95*color.a);
      #endif
      
      color.a *= v_color0.a;
      if(vDir.y >= 0.0){
      color.a *= smoothstep(0.1, 0.6, vDir.y);
      } else {
      color.a *= smoothstep(-0.1, -0.6, vDir.y);
      }
      
    #elif NL_CLOUD_TYPE == 3
      vDir.xz *= 0.3 + v_color0.w; // height parallax
     float a = 0.8; // or -ve
     float cosa = cos(a); float sina = sin(a);
      
      vec2 p = 3.0*vDir.xz/(0.08 + 0.25*abs(vDir.y));
           p = mul(p, mtxFromRows(vec2(cosa, sina), vec2(-sina, cosa)));
      vec4 clouds = renderClouds(p, v_color2.w, v_color1.w, v_color2.rgb, v_color1.rgb, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, NL_CLOUD3_SHADOW);
          float b = 0.8;
          float cosb = cos(b); float sinb = sin(b);
          
          vec2 pc = 3.5*vDir.xz/(0.01 + 0.5*abs(vDir.y));
           pc = mul(pc, mtxFromRows(vec2(cosb, sinb), vec2(-sinb, cosb)));
      vec4 cirrus = renderCloudCirrus(pc, v_color2.w, v_color1.w, v_color2.rgb, v_color1.rgb, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, 0.1);
      clouds = mix(cirrus, clouds, 0.3+0.8*clouds.a);
      vec3 additional = NL_DAWN_ZENITH_COL;
      additional *= max(0.0, 1.0)*dusk;
      clouds.rgb += additional;
      clouds.rgb *= 1.0-0.6*dusk;
      clouds.rgb *= 1.0+1.0*night;
      
      color = clouds;
      
      #ifdef NL_AURORA
        p.xy *= 34.7;
        color += renderAurora(p.xyy, v_color2.w, v_color1.w, v_fogColor)*(1.0-0.7*color.a);
      #endif

      color.a *= smoothstep(0.0, 0.6, vDir.y);
    #else
    color = vec4_splat(0.0);
    vec3 wpos = vDir/abs(vDir.y);
    float fade = smoothstep(50.0, 0.0,length(wpos.xz)) * smoothstep(0.0, 0.2,  vDir.y);
    color = VLClouds(normalize(v_color0.xyz), FogAndDistanceControl, FogColor, ViewPositionAndTime.w, v_color2.rgb, v_color1.rgb);
    color.a *= fade;
    if(wpos.y <= 0.0){discard;}
    #endif
    
    color.rgb = colorCorrection(color.rgb);
  #endif

  gl_FragColor = color;
}

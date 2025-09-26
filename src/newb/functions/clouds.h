#ifndef CLOUDS_H
#define CLOUDS_H

#include "noise.h"
#include "sky.h"

// simple clouds 2D noise
float cloudNoise2D(vec2 p, highp float t, float rain) {
  t *= NL_CLOUD1_SPEED;
  p += t;
  p.y += 3.0*sin(0.3*p.x + 0.1*t);

  vec2 p0 = floor(p);
  vec2 u = p-p0;
  u *= u*(3.0-2.0*u);
  vec2 v = 1.0-u;

  float n = mix(
    mix(rand(p0), rand(p0+vec2(1.0,0.0)), u.x),
    mix(rand(p0+vec2(0.0,1.0)), rand(p0+vec2(1.0,1.0)), u.x),
    u.y
  );
  n *= 0.5 + 0.5*sin(p.x*0.6 - 0.5*t)*sin(p.y*0.6 + 0.8*t);
  n = min(n*(1.0+rain), 1.0);
  return n*n;
}

// simple clouds
vec4 renderCloudsSimple(nl_skycolor skycol, vec3 pos, highp float t, float rain) {
  pos.xz *= NL_CLOUD1_SCALE;
  float d = cloudNoise2D(pos.xz, t, rain);
  vec4 col = vec4(skycol.horizonEdge + skycol.zenith, smoothstep(0.1,0.6,d));
  col.rgb += 1.5*dot(col.rgb, vec3(0.3,0.4,0.3))*smoothstep(0.6,0.2,d)*col.a;
  col.rgb *= 1.0 - 0.8*rain;
  return col;
}

// rounded clouds

// rounded clouds 3D density map
float cloudDf(sampler2D cloudTex, vec3 pos, float rain, vec2 boxiness) {
  boxiness *= 0.999;
  vec2 p0 = floor(pos.xz);
  vec2 u = max((pos.xz-p0-boxiness.x)/(1.0-boxiness.x), 0.0);
  u *= u*(3.0 - 2.0*u);

  vec4 r = vec4(
    texture2DLod(cloudTex, (p0*0.01), 0).r, 
    texture2DLod(cloudTex, (p0+vec2(1.0,0.0))*0.01, 0.0).r, 
    texture2DLod(cloudTex, (p0+vec2(1.0,1.0))*0.01, 0.0).r, 
    texture2DLod(cloudTex, (p0+vec2(0.0,1.0))*0.01, 0.0).r
  );
  r = smoothstep(0.8-0.25*rain, 1.0-0.35*rain*rain, r); // rain transition
  float n = mix(mix(r.x,r.y,u.x), mix(r.w,r.z,u.x), u.y);

  // round y
  n *= 1.0 - 1.5*smoothstep(boxiness.y, 2.0 - boxiness.y, 2.0*abs(pos.y-0.5));

  n = max(1.25*(n-0.2), 0.0); // smoothstep(0.2, 1.0, n)
  n *= n*(3.0 - 2.0*n);
  return n;
}

vec4 renderCloudsRounded(sampler2D cloudTex,
    vec3 vDir, vec3 vPos, float rain, float time, vec3 horizonCol, vec3 zenithCol,
    const int steps, const float thickness, const float thickness_rain, const float speed,
    const vec2 scale, const float density, const vec2 boxiness, vec3 FOG
) {
  float height = 7.0*mix(thickness, thickness_rain, rain);
  float stepsf = float(steps);

  // scaled ray offset
  vec3 deltaP;
  deltaP.y = 1.0;
  deltaP.xz = height*scale*vDir.xz/(0.02+0.98*abs(vDir.y));

  // local cloud pos
  vec3 pos;
  pos.y = 0.0;
  pos.xz = scale*(vPos.xz + vec2(1.0,0.5)*(time*speed));
  pos += deltaP;

  deltaP /= -stepsf;

  // alpha, gradient
  vec2 d = vec2(0.0,1.0);
  for (int i=1; i<=steps; i++) {
    float m = cloudDf(cloudTex, pos, rain, boxiness);
    d.x += m;
    d.y = mix(d.y, pos.y, m);
    pos += deltaP;
  }
  //d.y = smoothstep(0.2, 1.0, d.y*d.y);
  d.x *= smoothstep(1.3, 1.5, d.x);
  d.x /= (stepsf/density) + d.x;

  if (vPos.y < 0.0) { // view from top
    d.y = 1.0 - d.y;
  }

  vec4 col = vec4(horizonCol*1.5, d.x);
  col.rgb = mix(col.rgb, mix(col.rgb, zenithCol, 0.8), max(FOG.r - FOG.b, 0.0));
  col.rgb = mix(col.rgb, mix(col.rgb,zenithCol,0.7)*0.8, smoothstep(1.0, 0.2,d.y)); 
  return col;
}

float cloudsNoiseVr(vec2 p, float t) {
  float n = fastVoronoi2(3.5*p + t, 2.5);
  n *= fastVoronoi2(8.0*p + t, 1.5);
  n *= fastVoronoi2(24.0*p + t, 0.4);
  n *= fastVoronoi2(72.0*p + t, 0.1);
  //n *= fastVoronoi2(216.0*p + t, 0.02); // more quality
  //n *= fastVoronoi2(324.0*p + t, 0.006); // more quality
  
  return n*n;
}

vec4 renderClouds(vec2 p, float t, float rain, vec3 horizonCol, vec3 zenithCol, const vec2 scale, const float velocity, const float shadow) {
  p *= scale;
  t *= velocity;

  // layer 1
  p = 1.8 * p + vec2(6.8, 5.2) + vec2(6.8, 5.6) + vec2(5.6, 3.2);
  //p = 1.0- p.yx;
  p = p.yx;
  t *= 0.5;
  float a = cloudsNoiseVr(p, t);
  float b = cloudsNoiseVr(p + NL_CLOUD3_SHADOW_OFFSET*scale, t);


  // layer 2
  p=p.xy;
  p = 2.8 * p + vec2(5.8, 5.2);

  float c = cloudsNoiseVr(p, t);
  float d = cloudsNoiseVr(p + NL_CLOUD3_SHADOW_OFFSET*scale, t);
  vec2 po = p;
  float e = cloudsNoiseVr(p*0.8, t);
  c = mix(c, e, c);
  float f = cloudsNoiseVr(p*0.8 + NL_CLOUD3_SHADOW_OFFSET*scale, t);
  d = mix(d, f, d);
  
  // higher = less clouds thickness
  // lower separation betwen x & y = sharper
  vec2 tr = mix(vec2(0.78, 0.88), vec2(0.78, 1.2), rain)- 0.35 - 0.24*rain;
  vec2 trcd = mix(vec2(0.7, 0.85), vec2(0.7, 1.05), rain)- 0.35 - 0.26*rain;
  a = smoothstep(tr.x, tr.y, a);
  c = smoothstep(trcd.x, trcd.y, c);

  // shadow
  b *= smoothstep(0.2, 0.8, b);
  d *= smoothstep(0.2, 0.8, d);
  
  vec4 col;
  col.a = c + a*(1.0-c);
  col.rgb =  (mix(horizonCol, zenithCol ,0.8)+horizonCol)*0.4 ;
  col.rgb = mix(col.rgb, zenithCol*0.95, shadow*mix(d, b, a));
  col.rgb *= 1.0-0.5*rain;
  
  return col;
}

float noise2DCr(vec2 pos, float t) {
  float n = fastVoronoiCirrus(2.5*pos + t, 2.5);
  n *= fastVoronoiCirrus(4.0*pos + t, 1.5);
  n *= fastVoronoiCirrus(12.0*pos + t, 0.4);
  return n*n;
}

vec4 renderCloudCirrus(vec2 p, float t, float rain, vec3 horizonCol, vec3 zenithCol, const vec2 scale, const float velocity, const float shadow) {
  p *= scale;
  t *= velocity;

  // layer 1
  float a = noise2DCr(p, t);
  float b = noise2DCr(p + NL_CLOUD3_SHADOW_OFFSET*scale, t);
 a *=0.0;
 b*=a;
  // layer 2
  p = 4.5 * p + vec2(5.8, 5.8);
  t *= 0.5;
  float c = noise2DCr(p, t);
  float d = noise2DCr(p + NL_CLOUD3_SHADOW_OFFSET*scale, t);

  // higher = less clouds thickness
  // lower separation betwen x & y = sharper
  vec2 tr = vec2(0.35, 2.8) -0.4 - 0.9*rain;
  a = smoothstep(tr.x, tr.y, a);
  c = smoothstep(tr.x, tr.y, c);

  // shadow
  b *= smoothstep(0.2, 0.8, b);
  d *= smoothstep(0.2, 0.8, d);

  vec4 col;
  col.a = a + c*(1.0-a);
  col.rgb =  (mix(horizonCol, zenithCol ,0.8)+horizonCol)*0.4 ;
  col.rgb = mix(col.rgb, zenithCol, shadow*mix(b, d, c));
  col.rgb *= 1.0-0.5*rain;
  return col;
}

highp float fbm(vec3 p, float t, float rain) {
  float f = 0.0;
  float amp = 0.5;
  p.xz += 0.01*t;
  for (int i = 0; i <= V_CLOUD_DETAIL_QUALITY; i++) {
    f += amp * noise3D(p + vec3(0.05, 0.05, 0.0)*t);
    p *= V_CLOUD_DETAIL;
    amp *= mix(0.5, 0.55, rain) ;
  }
  return smoothstep(0.0,0.99,f + 0.2/4.0);
}

vec4 VLClouds(vec3 viewDir, vec4 FogAndDistanceControl, vec4 FogColor, float time, vec3 horizon, vec3 zenith) {
    time *= 0.5;
    float dusk = max(FogColor.r - FogColor.b, 0.0);
    float cloudBase = 1.0;
    float cloudTop = 1.3;
    int steps = V_CLOUD_STEPS;
    float stepSize = (cloudTop - cloudBase) / float(steps);

    float rain = mix(smoothstep(0.66, 0.3, FogAndDistanceControl.x), 0.0, step(FogAndDistanceControl.x, 0.0));
    vec3 cloudAccum = vec3_splat(0.0);

    float alphaAccum = 0.0;
    float jitter = fract(sin(dot(viewDir.xz, vec2_splat(332.233))) * 87758.5453);
    for (int i = 0; i <= steps ; i++) {
        float height = cloudBase + stepSize * (float(i)+jitter);
        float t = V_CLOUD_HEIGHT*height / abs(0.1+viewDir.y);
        vec3 pos = viewDir * t ;

        vec3 noisePos = vec3(pos.xz + 50.5, height*0.8);
        float base = fbm(noisePos, time, rain);

        float heightNorm = (height - cloudBase) / (cloudTop - cloudBase);
        float heightFactor = smoothstep(0.0, 1.0, heightNorm) * (1.0 - smoothstep(0.6, 1.0, heightNorm));
        heightFactor *= smoothstep(0.2, 0.6, base);

        float density = 1.5*clamp(base - 0.5, 0.0, 1.0);
        density = pow(density, 3.0) * heightFactor;

        float alpha = 1.0 - smoothstep(0.01, 0.0025, density);
        alpha *= (1.0 - alphaAccum);

       float scattering = smoothstep(0.0, 0.9, heightNorm);
       float night = pow(max(min(1.0 - FogColor.r * 1.5, 1.0), 0.0), 1.2);
        vec3 cloudColor = mix(mix(horizon, mix( horizon ,zenith, 0.9), dusk) , mix(horizon, zenith, mix(1.0, 0.8, night))*mix(0.7,1.0, night), 1.0-scattering);

        cloudAccum += cloudColor * alpha;
        alphaAccum += alpha;

        if (alphaAccum > 0.999 && viewDir.y < 0.9) break;
    }

      vec4 clouds = vec4(mix(0.5*(mix(horizon, zenith, mix(0.1,1.0, dusk))+horizon+zenith*dusk), cloudAccum, alphaAccum), alphaAccum);
      clouds.rgb *= 1.0-0.1*rain;
      return clouds;
}

// aurora is rendered on clouds layer
#ifdef NL_AURORA
vec4 renderAurora(vec3 p, float t, float rain, vec3 FOG_COLOR) {
  t *= NL_AURORA_VELOCITY;
  p.xz *= NL_AURORA_SCALE;
  p.xz += 0.05*sin(p.x*4.0 + 20.0*t);

  float d0 = sin(p.x*0.1 + t + sin(p.z*0.2));
  float d1 = sin(p.z*0.1 - t + sin(p.x*0.2));
  float d2 = sin(p.z*0.1 + 1.0*sin(d0 + d1*2.0) + d1*2.0 + d0*1.0);
  d0 *= d0; d1 *= d1; d2 *= d2;
  d2 = d0/(1.0 + d2/NL_AURORA_WIDTH);
   
  float mask = (1.0-0.8*rain)*max(1.0 - 4.0*max(FOG_COLOR.b, FOG_COLOR.g), 0.0)*pow(max(min(1.0 - FOG_COLOR.r * 1.5, 1.0), 0.0), 1.2);
  return vec4(NL_AURORA*mix(NL_AURORA_COL1,NL_AURORA_COL2,d1),1.0)*d2*mask;
}
#endif

#endif

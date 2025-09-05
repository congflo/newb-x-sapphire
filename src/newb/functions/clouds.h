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
    mix(rand(p0),rand(p0+vec2(1.0,0.0)), u.x),
    mix(rand(p0+vec2(0.0,1.0)),rand(p0+vec2(1.0,1.0)), u.x),
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
float cloudDf(vec3 pos, float rain, vec2 boxiness) {
  boxiness *= 0.999;
  vec2 p0 = floor(pos.xz);
  vec2 u = max((pos.xz-p0-boxiness.x)/(1.0-boxiness.x), 0.0);
  u *= u*(3.0 - 2.0*u);

  vec4 r = vec4(rand(p0), rand(p0+vec2(1.0,0.0)), rand(p0+vec2(1.0,1.0)), rand(p0+vec2(0.0,1.0)));
  r = smoothstep(0.2051+0.2*rain, 0.205+0.2*rain*rain, r); // rain transition

  float n = mix(mix(r.x,r.y,u.x), mix(r.w,r.z,u.x), u.y);

  // round y
  n *= 1.0 - 1.5*smoothstep(boxiness.y, 2.0 - boxiness.y, 2.0*abs(pos.y-0.5));

  n = max(1.25*(n-0.2), 0.0); // smoothstep(0.2, 1.0, n)
  n *= n*(3.0 - 2.0*n);
  return n;
}

vec4 renderCloudsRounded(
    vec3 vDir, vec3 vPos, float rain, float time, vec3 horizonCol, vec3 zenithCol,
    const int steps, const float thickness, const float thickness_rain, const float speed,
    const vec2 scale, const float density, const vec2 boxiness
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
    float m = cloudDf(pos, rain, boxiness);
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
  col.rgb = mix(col.rgb, mix(col.rgb,zenithCol,0.8), smoothstep(1.0, 0.2,d.y)); 
  col.a *= 0.85;
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

  float mask = (1.0-0.8*rain)*max(1.0 - 4.0*max(FOG_COLOR.b, FOG_COLOR.g), 0.0);
  return vec4(NL_AURORA*mix(NL_AURORA_COL1,NL_AURORA_COL2,d1),1.0)*d2*mask;
}
#endif

#endif

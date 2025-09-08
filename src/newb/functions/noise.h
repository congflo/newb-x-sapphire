#ifndef NOISE_H
#define NOISE_H

#include "constants.h"
SAMPLER2D_AUTOREG(s_NoiseTexture);

// functions under [1] are from https://gist.github.com/patriciogonzalezvivo/670c22f3966e662d2f83

// [1] hash function for noise (for highp only)
float rand(highp vec2 n) {
  return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

// 1D noise - used in plants,lantern wave
float noise1D(highp float x) {
  return texture2DLod(s_NoiseTexture, vec2_splat(x)*0.0001, 0.0).g;
}

// simpler rand for disp, puddles
float fastRand(vec2 n){
  return fract(37.45*sin(dot(n, vec2(4.36, 8.28))));
}

// water displacement map (also used by caustic)
float disp(vec3 pos, float t) {
  float n = sin(8.0*NL_CONST_PI_HALF*(pos.x+pos.y*pos.z) + 0.7*t);
  pos.y += t + 0.8*n;
  float p = floor(pos.y);
  return (0.8+0.2*n) * mix(fastRand(pos.xz+p), fastRand(pos.xz+p+1.0), pos.y - p);
}

float noise2D(vec2 u) {
  return texture2DLod(s_NoiseTexture, u*0.01, 0.0).b;
}

 vec4 mod289(vec4 x) {
  return x - floor(x * (1.0 / 289.0)) * 289.0;
}

 vec4 perm(vec4 x) {
  return mod289(((x * 34.0) + 1.0) * x);
}

 float noise3D(vec3 p) {
   vec3 a = floor(p);
   vec3 d = p - a;

   vec4 b = a.xxyy + vec4(0.0, 1.0, 0.0, 1.0);
   vec4 k1 = perm(b.xyxy);
   vec4 k2 = perm(k1.xyxy + b.zzww);

   vec4 c = k2 + a.zzzz;
   vec4 k3 = perm(c);
   vec4 k4 = perm(c + 1.0);
   vec4 o1 = fract(k3 / 41.0);
   vec4 o2 = fract(k4 / 41.0);
   vec4 o3 = o2 * d.z + o1 * (1.0 - d.z);
   vec2 o4 = o3.yw * d.x + o3.xz * (1.0 - d.x);

  return o4.y * d.y + o4.x * (1.0 - d.y);

}


float fastVoronoi2(vec2 pos, float f) {
  vec4 p = pos.xyxy;
  p.zw += p.wz*mix(vec2(0.895,-0.937), vec2(0.937,-0.698), vec2(0.698, -0.837));
  p = fract(p) - 0.5;
  p *= p;
  return 1.0-f*min(p.x+p.y, p.z+p.w);
}

float fastVoronoiCirrus(vec2 pos, float f) {
  vec4 p = pos.xyxy;
  p.zw += p.wz*mix(vec2(-0.15,-0.75), vec2(-1.06,0.95), vec2(0.99,-0.78));
  p.yz += p.xz*mix(vec2(-0.05,-1.05), vec2(-0.5,2.0), 1.0);
  p = fract(p) - 0.5;
  p *= p;
  return 1.0-f*min(p.x+p.y, p.z+p.w);
}

#endif

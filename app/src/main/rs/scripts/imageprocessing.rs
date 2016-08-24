#pragma version(1)
#pragma rs java_package_name(com.dzo.fit.sketchfit)
#pragma rs_fp_relaxed

// size of Laplace matrix
#define LM_SIZE 3
// size of Gauss matrix (bilateral filter - pixel position distance)
#define GM_SIZE 5
// size of Gauss matrix (noice reduction)
#define GSM_SIZE 3
// sigma of gauss function (bilateral filter - pixel intensity distance)
#define GAUSS_SIGMA 0.1f // for intensity values in float3 image space

// pre-evaluated multiplier for transforming byte RGBa values [0;255] to float RGB [0;1]
const float normU8 = 1.f/255.f;
// radiuses of matrices used
const uint lmBorder = (LM_SIZE - 1) / 2;
const uint gmBorder = (GM_SIZE - 1) / 2;
const uint gsmBorder = (GSM_SIZE - 1) / 2;
// pre-evaluated multiplier in gauss function exponent (bilateral filter)
// they are evaluated in "init" function
float gaussExpMult;
float gaussMult;

// Gauss matrix used on camera input image to reduce noice by smoothing
static float gaussSmoothMatrix[GSM_SIZE][GSM_SIZE] = {
    {0.109021f,	0.112141f,	0.109021f},
    {0.112141f,	0.11535f,	0.112141f},
    {0.109021f,	0.112141f,	0.109021f}
};

// Laplace matrix for edge detection
static float laplaceMatrix[LM_SIZE][LM_SIZE] = {
    {0.f, 1.f, 0.f},
    {1.f, -4.f, 1.f},
    {0.f, 1.f, 0.f}
};

// Gauss matrix used in bilateral filter (pixel position distance)
static float gaussMatrix[GM_SIZE][GM_SIZE] = {
    {0.010534f,	0.02453f,	0.032508f,	0.02453f,	0.010534f},
    {0.02453f,	0.05712f,	0.075698f,	0.05712f,	0.02453f},
    {0.032508f,	0.075698f,	0.100318f,	0.075698f,	0.032508f},
    {0.02453f,	0.05712f,	0.075698f,	0.05712f,	0.02453f},
    {0.010534f,	0.02453f,	0.032508f,	0.02453f,	0.010534f}
};

// camera input resolution parameters (2x higher than output)
uint imageWidth;
uint imageHeight;
// filter output resolution parameters
uint sImageWidth;
uint sImageHeight;
// edge detection treshold
float treshold;
// buffer containing input image buffer for kernels
rs_allocation floatBuffer;
// buffer containing evaluated edges
rs_allocation edgeBuffer;

inline static float3 zero_float3() {
    float3 c;
    c.r = 0.f;
    c.g = 0.f;
    c.b = 0.f;
    return c;
}

// Evaluates value of a gauss function (normal distribution with pre-evaluated exponent multiplier)
inline static float gauss(float x) {
    return gaussMult * native_exp(gaussExpMult * x * x);
}

// Evaluates intensity of an RGB pixel = lenght of the vector
inline static float intensity(float3 rgb) {
    return fast_length(rgb);
}

// Evaluates intensity difference between two RGB pixels
// Applies logarithm and division to reduce "halo" effect around edges
inline static float intensityDifference(float3 x, float3 y) {
    return native_log((intensity(x) + 0.01f) / (intensity(y) + 0.01f));
}

void init() {
    gaussExpMult = - 1.f / (2.f * GAUSS_SIGMA * GAUSS_SIGMA);
    gaussMult = 1.f / sqrt(2.f * M_PI * GAUSS_SIGMA * GAUSS_SIGMA);
}

// Translates RGBa byte data of an image to RGB float data
void __attribute__((kernel)) initFloatBuffer(uchar4 in, uint32_t x, uint32_t y) {
    float3 fRGB;
    fRGB.r = normU8 * in.r;
    fRGB.g = normU8 * in.g;
    fRGB.b = normU8 * in.b;
    rsSetElementAt_float3(floatBuffer, fRGB, x, y);
}

// Applies convolution of 2D gauss function on a image (smoothing - noise reduction)
float3 __attribute__((kernel)) applySmooth(uint32_t x, uint32_t y) {
    float3 resPixel = zero_float3();

    for (uint i = 0; i < GSM_SIZE; ++i) {
        for (uint j = 0; j < GSM_SIZE; ++j) {
            float gauss = gaussSmoothMatrix[i][j];
            float3 pixel = rsGetElementAt_float3(floatBuffer, clamp(x + i - gsmBorder, 0u, imageWidth - 1u), clamp(y + j - gsmBorder, 0u, imageHeight - 1u));
            resPixel += gauss * pixel;
        }
    }

    return resPixel;
}

// Applies edge detection on an image
// Resulting pixels are of two possible values: {0, 1}
float __attribute__((kernel)) applyEdgeDetection(uint32_t x, uint32_t y) {
    float resIntensity = 0.f;

    for (uint i = 0; i < LM_SIZE; ++i) {
        for (uint j = 0; j < LM_SIZE; ++j) {
            float gauss = laplaceMatrix[i][j];
            float3 pixel = rsGetElementAt_float3(floatBuffer, clamp(x + i - lmBorder, 0u, imageWidth - 1u), clamp(y + j - lmBorder, 0u, imageHeight - 1u));
            resIntensity += gauss * intensity(pixel);
        }
    }

    return resIntensity > treshold ? 0.f : 1.f;
}

// Applies bilateral filter on an image
float3 __attribute__((kernel)) applyBilateralFilter_float3(uint32_t x, uint32_t y) {
    float3 result = zero_float3();
    float W = 0.f;

    for (uint i = 0; i < GM_SIZE; ++i) {
        for (uint j = 0; j < GM_SIZE; ++j) {
            float gm = gaussMatrix[i][j];
            float3 origin = rsGetElementAt_float3(floatBuffer, x, y);
            float3 pixel = rsGetElementAt_float3(floatBuffer, clamp(x + i - gmBorder, 0u, sImageWidth - 1u), clamp(y + j - gmBorder, 0u, sImageHeight - 1u));
            float ev = gm * gauss(intensityDifference(pixel, origin));
            W += ev;
            result += ev * pixel;
        }
    }

    return result / W;
}

// Scales down image resolution to half (output pixel is average of 4 pixels)
float3 __attribute__((kernel)) imgScaleDown(uint32_t x, uint32_t y) {
    uint32_t xOff = x * 2;
    uint32_t yOff = y * 2;
    float3 resultPixel = zero_float3();

    for (int i = 0; i < 2; ++i) {
        for (int j = 0; j < 2; ++j) {
            resultPixel += rsGetElementAt_float3(floatBuffer, xOff + i, yOff + j);
        }
    }

    resultPixel = 0.25f * resultPixel;
    return resultPixel;
}

// Applies bilateral filter and transforms resulting float RGB pixel to byte RGBa pixel
uchar4 __attribute__((kernel)) applyBilateralFilter_uchar4(uint32_t x, uint32_t y) {
    return rsPackColorTo8888(applyBilateralFilter_float3(x, y));
}

// Multiplies the average edge value [0;1] of 4 pixels with current pixel RGB value
// Edges are stored in 2x higher resolution
float3 __attribute__((kernel)) applyEdges(uint32_t x, uint32_t y) {
    uint32_t xOff = x * 2;
    uint32_t yOff = y * 2;
    float resultEdge = 0.f;

    for (int i = 0; i < 2; ++i) {
        for (int j = 0; j < 2; ++j) {
            resultEdge += rsGetElementAt_float(edgeBuffer, xOff + i, yOff + j);
        }
    }

    return 0.25f * resultEdge * rsGetElementAt_float3(floatBuffer, x, y);
}
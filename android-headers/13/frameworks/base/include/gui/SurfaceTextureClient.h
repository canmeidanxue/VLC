/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_GUI_SURFACETEXTURECLIENT_H
#define ANDROID_GUI_SURFACETEXTURECLIENT_H

#include <gui/ISurfaceTexture.h>
#include <gui/SurfaceTexture.h>

#include <ui/egl/android_natives.h>

#include <utils/RefBase.h>
#include <utils/threads.h>

namespace android {

class SurfaceTextureClient
    : public EGLNativeBase<ANativeWindow, SurfaceTextureClient, RefBase>
{
public:
    SurfaceTextureClient(const sp<ISurfaceTexture>& surfaceTexture);

    sp<ISurfaceTexture> getISurfaceTexture() const;

private:

    // can't be copied
    SurfaceTextureClient& operator = (const SurfaceTextureClient& rhs);
    SurfaceTextureClient(const SurfaceTextureClient& rhs);

    // ANativeWindow hooks
    static int cancelBuffer(ANativeWindow* window, android_native_buffer_t* buffer);
    static int dequeueBuffer(ANativeWindow* window, android_native_buffer_t** buffer);
    static int lockBuffer(ANativeWindow* window, android_native_buffer_t* buffer);
    static int perform(ANativeWindow* window, int operation, ...);
    static int query(ANativeWindow* window, int what, int* value);
    static int queueBuffer(ANativeWindow* window, android_native_buffer_t* buffer);
    static int setSwapInterval(ANativeWindow* window, int interval);

    int cancelBuffer(android_native_buffer_t* buffer);
    int dequeueBuffer(android_native_buffer_t** buffer);
    int lockBuffer(android_native_buffer_t* buffer);
    int perform(int operation, va_list args);
    int query(int what, int* value);
    int queueBuffer(android_native_buffer_t* buffer);
    int setSwapInterval(int interval);

    int dispatchConnect(va_list args);
    int dispatchDisconnect(va_list args);
    int dispatchSetBufferCount(va_list args);
    int dispatchSetBuffersGeometry(va_list args);
    int dispatchSetBuffersTransform(va_list args);
    int dispatchSetCrop(va_list args);
    int dispatchSetUsage(va_list args);

    int connect(int api);
    int disconnect(int api);
    int setBufferCount(int bufferCount);
    int setBuffersGeometry(int w, int h, int format);
    int setBuffersTransform(int transform);
    int setCrop(Rect const* rect);
    int setUsage(uint32_t reqUsage);

    void freeAllBuffers();

    enum { MIN_UNDEQUEUED_BUFFERS = SurfaceTexture::MIN_UNDEQUEUED_BUFFERS };
    enum { MIN_BUFFER_SLOTS = SurfaceTexture::MIN_BUFFER_SLOTS };
    enum { NUM_BUFFER_SLOTS = SurfaceTexture::NUM_BUFFER_SLOTS };
    enum { DEFAULT_FORMAT = PIXEL_FORMAT_RGBA_8888 };

    // mSurfaceTexture is the interface to the surface texture server. All
    // operations on the surface texture client ultimately translate into
    // interactions with the server using this interface.
    sp<ISurfaceTexture> mSurfaceTexture;

    // mAllocator is the binder object that is referenced to prevent the
    // dequeued buffers from being freed prematurely.
    sp<IBinder> mAllocator;

    // mSlots stores the buffers that have been allocated for each buffer slot.
    // It is initialized to null pointers, and gets filled in with the result of
    // ISurfaceTexture::requestBuffer when the client dequeues a buffer from a
    // slot that has not yet been used. The buffer allocated to a slot will also
    // be replaced if the requested buffer usage or geometry differs from that
    // of the buffer allocated to a slot.
    sp<GraphicBuffer> mSlots[NUM_BUFFER_SLOTS];

    // mReqWidth is the buffer width that will be requested at the next dequeue
    // operation. It is initialized to 1.
    uint32_t mReqWidth;

    // mReqHeight is the buffer height that will be requested at the next deuque
    // operation. It is initialized to 1.
    uint32_t mReqHeight;

    // mReqFormat is the buffer pixel format that will be requested at the next
    // deuque operation. It is initialized to PIXEL_FORMAT_RGBA_8888.
    uint32_t mReqFormat;

    // mReqUsage is the set of buffer usage flags that will be requested
    // at the next deuque operation. It is initialized to 0.
    uint32_t mReqUsage;

    // mMutex is the mutex used to prevent concurrent access to the member
    // variables of SurfaceTexture objects. It must be locked whenever the
    // member variables are accessed.
    Mutex mMutex;
};

}; // namespace android

#endif  // ANDROID_GUI_SURFACETEXTURECLIENT_H

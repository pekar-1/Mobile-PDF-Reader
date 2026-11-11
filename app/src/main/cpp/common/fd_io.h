#pragma once

// File-descriptor based I/O adapters for PDFium. They work with any fd,
// including ParcelFileDescriptor obtained from a content:// Uri (SAF).

#include <memory>

#include "fpdf_save.h"
#include "fpdfview.h"

namespace pdfjni {

// Read adapter for FPDF_LoadCustomDocument. Owns a dup() of the caller's fd,
// so the caller may close its own ParcelFileDescriptor right after opening.
// Must outlive the FPDF_DOCUMENT that reads from it.
class FdFileAccess {
public:
    // Returns nullptr if the fd cannot be duplicated or its size read.
    static std::unique_ptr<FdFileAccess> create(int fd);

    ~FdFileAccess();
    FdFileAccess(const FdFileAccess&) = delete;
    FdFileAccess& operator=(const FdFileAccess&) = delete;

    FPDF_FILEACCESS* access() { return &access_; }

private:
    FdFileAccess(int fd, unsigned long length);
    static int getBlock(void* param, unsigned long position,
                        unsigned char* buffer, unsigned long size);

    FPDF_FILEACCESS access_{};
    int fd_;
};

// Write adapter for FPDF_SaveAsCopy. Does not own the fd and does not close
// it. FPDF_FILEWRITE must stay the first member: PDFium passes it back as
// `self` and we cast to the enclosing struct.
struct FdFileWrite {
    FPDF_FILEWRITE base;
    int fd;
    bool failed;

    explicit FdFileWrite(int fd);

private:
    static int writeBlock(FPDF_FILEWRITE* self, const void* data,
                          unsigned long size);
};

}  // namespace pdfjni

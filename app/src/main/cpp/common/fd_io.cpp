#include "common/fd_io.h"

#include <cerrno>
#include <cstddef>
#include <sys/stat.h>
#include <unistd.h>

namespace pdfjni {

static_assert(offsetof(FdFileWrite, base) == 0,
              "FPDF_FILEWRITE must be the first member of FdFileWrite");

std::unique_ptr<FdFileAccess> FdFileAccess::create(int fd) {
    if (fd < 0) return nullptr;
    const int owned = dup(fd);
    if (owned < 0) return nullptr;

    struct stat info {};
    if (fstat(owned, &info) != 0 || info.st_size <= 0) {
        close(owned);
        return nullptr;
    }
    return std::unique_ptr<FdFileAccess>(
        new FdFileAccess(owned, static_cast<unsigned long>(info.st_size)));
}

FdFileAccess::FdFileAccess(int fd, unsigned long length) : fd_(fd) {
    access_.m_FileLen = length;
    access_.m_GetBlock = &FdFileAccess::getBlock;
    access_.m_Param = this;
}

FdFileAccess::~FdFileAccess() {
    if (fd_ >= 0) close(fd_);
}

int FdFileAccess::getBlock(void* param, unsigned long position,
                           unsigned char* buffer, unsigned long size) {
    auto* self = static_cast<FdFileAccess*>(param);
    unsigned long done = 0;
    while (done < size) {
        const ssize_t read = pread(self->fd_, buffer + done, size - done,
                                   static_cast<off_t>(position + done));
        if (read < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        if (read == 0) return 0;  // Unexpected EOF.
        done += static_cast<unsigned long>(read);
    }
    return 1;
}

FdFileWrite::FdFileWrite(int fd) : base{}, fd(fd), failed(false) {
    base.version = 1;
    base.WriteBlock = &FdFileWrite::writeBlock;
}

int FdFileWrite::writeBlock(FPDF_FILEWRITE* self, const void* data,
                            unsigned long size) {
    auto* writer = reinterpret_cast<FdFileWrite*>(self);
    const auto* bytes = static_cast<const unsigned char*>(data);
    unsigned long done = 0;
    while (done < size) {
        const ssize_t written = write(writer->fd, bytes + done, size - done);
        if (written < 0) {
            if (errno == EINTR) continue;
            writer->failed = true;
            return 0;
        }
        done += static_cast<unsigned long>(written);
    }
    return 1;
}

}  // namespace pdfjni

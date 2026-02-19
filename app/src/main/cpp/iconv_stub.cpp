// Stub implementation of Iconv for Android (no iconv support)
#include <string>

// MeCab Iconv stubs
namespace MeCab {
    class Iconv {
    public:
        Iconv() {}
        ~Iconv() {}
        
        bool open(const char* from, const char* to) {
            // Android では UTF-8 のみ対応と仮定
            return true;
        }
        
        bool convert(std::string* str) {
            // 変換なし（UTF-8 のまま）
            return true;
        }
    };
}

// CaboCha Iconv stubs
namespace CaboCha {
    enum CharsetType {
        EUC_JP = 0,
        CP932  = 1,
        UTF8   = 2,
        ASCII  = 3
    };
    
    class Iconv {
    public:
        Iconv() {}
        ~Iconv() {}
        
        bool open(CharsetType from, CharsetType to) {
            // Android では UTF-8 のみ対応と仮定
            return true;
        }
        
        bool open(const char* from, const char* to) {
            return true;
        }
        
        bool convert(std::string* str) {
            // 変換なし（UTF-8 のまま）
            return true;
        }
    };
    
    const char* decode_charset(const char* str) {
        // デフォルトで UTF-8 を返す
        return "UTF-8";
    }
    
    const char* encode_charset(CharsetType charset) {
        switch (charset) {
            case EUC_JP: return "EUC-JP";
            case CP932:  return "CP932";
            case UTF8:   return "UTF-8";
            case ASCII:  return "ASCII";
            default:     return "UTF-8";
        }
    }
}

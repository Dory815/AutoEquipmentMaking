"""Small dependency-free, read-only NBT inspector for local snapshot evidence."""
import array
import gzip
import io
import struct
import sys
import uuid

def read_file(path):
    return read_bytes(gzip.open(path,'rb').read())

def read_bytes(data):
    stream=io.BytesIO(data)
    def number(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize(fmt)))[0]
    def string():return stream.read(number('H')).decode('utf-8',errors='replace')
    def read(kind):
        if kind in range(1,7):return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7:return stream.read(number('i'))
        if kind==8:return string()
        if kind==9:
            subtype=number('B');count=number('i');return [read(subtype) for _ in range(count)]
        if kind==10:
            result={}
            while True:
                subtype=number('B')
                if not subtype:return result
                name=string();result[name]=read(subtype)
        if kind in (11,12):
            count=number('i');result=array.array('i' if kind==11 else 'q');result.frombytes(stream.read(count*(4 if kind==11 else 8)))
            if sys.byteorder=='little':result.byteswap()
            return result
        raise ValueError(kind)
    assert number('B')==10
    string()
    return read(10)

def entity_uuid(tag):
    return str(uuid.UUID(bytes=b''.join((v&0xffffffff).to_bytes(4,'big') for v in tag['UUID'])))

if __name__=='__main__':
    data=read_file(sys.argv[1])
    target=sys.argv[2] if len(sys.argv)>2 else None
    for tag in data.get('entities',[]):
        if target is None or entity_uuid(tag)==target:
            print(entity_uuid(tag),tag.get('id'),tag.get('Pos'), 'passengers',len(tag.get('Passengers',[])))
            if target:print({k:v for k,v in tag.items() if k not in ['Brain','Offers','Gossips']})

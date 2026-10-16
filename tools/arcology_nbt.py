"""Lossless-type NBT I/O for local blueprint transformations (standard library only)."""
import gzip,struct,os,tempfile
from dataclasses import dataclass
@dataclass
class Tag:
 kind:int
 value:object
class Reader:
 def __init__(self,data):self.data=data;self.p=0
 def read(self,n):
  if n<0:raise ValueError('negative NBT length')
  b=self.data[self.p:self.p+n];self.p+=n
  if len(b)!=n:raise ValueError('truncated NBT')
  return b
 def num(self,f):return struct.unpack('>'+f,self.read(struct.calcsize('>'+f)))[0]
 def string(self):return self.read(self.num('H')).decode('utf-8')
 def tag(self,k):
  if k in range(1,7):v=self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[k])
  elif k==7:v=list(self.read(self.num('i')))
  elif k==8:v=self.string()
  elif k==9:
   subtype=self.num('B');v=(subtype,[self.tag(subtype) for _ in range(self.num('i'))])
  elif k==10:
   v={}
   while True:
    subtype=self.num('B')
    if subtype==0:break
    name=self.string();v[name]=self.tag(subtype)
  elif k in (11,12):v=[self.num('i' if k==11 else 'q') for _ in range(self.num('i'))]
  else:raise ValueError('unknown NBT tag '+str(k))
  return Tag(k,v)
def plain(t):
 if t.kind==10:return {k:plain(v) for k,v in t.value.items()}
 if t.kind==9:return [plain(v) for v in t.value[1]]
 return t.value

def rebuild(value,template=None):
 kind=template.kind if template else (10 if isinstance(value,dict) else 9 if isinstance(value,list) else 8 if isinstance(value,str) else 6 if isinstance(value,float) else 3)
 if kind==10:
  old=template.value if template else {};return Tag(10,{k:rebuild(v,old.get(k)) for k,v in value.items()})
 if kind==9:
  subtype,old=template.value if template else (0,[])
  entries=[rebuild(v,old[i] if i<len(old) else (old[0] if old else None)) for i,v in enumerate(value)]
  subtype=entries[0].kind if entries else subtype
  if any(e.kind!=subtype for e in entries):raise ValueError('mixed list')
  return Tag(9,(subtype,entries))
 return Tag(kind,value)
def string(s):
 b=s.encode('utf-8');return struct.pack('>H',len(b))+b

def encode(t):
 k,v=t.kind,t.value
 if k in range(1,7):return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[k],v)
 if k==7:return struct.pack('>i',len(v))+bytes(v)
 if k==8:return string(v)
 if k==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(encode(x) for x in v[1])
 if k==10:return b''.join(bytes([x.kind])+string(name)+encode(x) for name,x in v.items())+b'\0'
 if k in (11,12):return struct.pack('>i',len(v))+b''.join(struct.pack('>i' if k==11 else '>q',x) for x in v)
 raise ValueError(k)
def load(path):
 r=Reader(gzip.decompress(path.read_bytes()));kind=r.num('B');name=r.string();tag=r.tag(kind)
 if r.p!=len(r.data) or kind!=10:raise ValueError('invalid root')
 return plain(tag),tag,name

def save(path,value,template,name=''):
 data=bytes([10])+string(name)+encode(rebuild(value,template));path.parent.mkdir(parents=True,exist_ok=True)
 temporary=None
 try:
  with tempfile.NamedTemporaryFile(dir=path.parent,prefix=path.name+'.',suffix='.tmp',delete=False) as stream:
   temporary=stream.name;stream.write(gzip.compress(data,mtime=0))
  os.replace(temporary,path);temporary=None
 finally:
  if temporary is not None:os.unlink(temporary)

def block(n,pos):
 x,y,z=pos
 if not(0<=x<n['size_x'] and 0<=y<n['size_y'] and 0<=z<n['size_z']):return None
 i=(y*n['size_z']+z)*n['size_x']+x;idx=(n['blocks'][i//2]>>(16 if i%2==0 else 0))&65535
 return n['palette'][idx]

def set_block(n,pos,state):
 if block(n,pos) is None:raise ValueError('block outside blueprint')
 try:index=n['palette'].index(state)
 except ValueError:index=len(n['palette']);n['palette'].append(state.copy())
 if index>65535:raise ValueError('blueprint palette overflow')
 x,y,z=pos;i=(y*n['size_z']+z)*n['size_x']+x;shift=16 if i%2==0 else 0
 packed=((n['blocks'][i//2]&0xffffffff)&~(65535<<shift))|(index<<shift)
 n['blocks'][i//2]=packed if packed<2147483648 else packed-4294967296

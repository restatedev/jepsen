/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata1 } from "./dev/restate/ext";

export const protobufPackage = "org.example";

export interface ReadRequest {
  key: string;
}

export interface ReadResponse {
  value: number;
}

export interface WriteRequest {
  key: string;
  value: number;
}

export interface WriteResponse {
}

function createBaseReadRequest(): ReadRequest {
  return { key: "" };
}

export const ReadRequest = {
  encode(message: ReadRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== "") {
      writer.uint32(10).string(message.key);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ReadRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseReadRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.key = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ReadRequest {
    return { key: isSet(object.key) ? String(object.key) : "" };
  },

  toJSON(message: ReadRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = message.key);
    return obj;
  },

  create(base?: DeepPartial<ReadRequest>): ReadRequest {
    return ReadRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ReadRequest>): ReadRequest {
    const message = createBaseReadRequest();
    message.key = object.key ?? "";
    return message;
  },
};

function createBaseReadResponse(): ReadResponse {
  return { value: 0 };
}

export const ReadResponse = {
  encode(message: ReadResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.value !== 0) {
      writer.uint32(8).int32(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ReadResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseReadResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.value = reader.int32();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ReadResponse {
    return { value: isSet(object.value) ? Number(object.value) : 0 };
  },

  toJSON(message: ReadResponse): unknown {
    const obj: any = {};
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<ReadResponse>): ReadResponse {
    return ReadResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ReadResponse>): ReadResponse {
    const message = createBaseReadResponse();
    message.value = object.value ?? 0;
    return message;
  },
};

function createBaseWriteRequest(): WriteRequest {
  return { key: "", value: 0 };
}

export const WriteRequest = {
  encode(message: WriteRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== "") {
      writer.uint32(10).string(message.key);
    }
    if (message.value !== 0) {
      writer.uint32(16).int32(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): WriteRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseWriteRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.key = reader.string();
          continue;
        case 2:
          if (tag != 16) {
            break;
          }

          message.value = reader.int32();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): WriteRequest {
    return { key: isSet(object.key) ? String(object.key) : "", value: isSet(object.value) ? Number(object.value) : 0 };
  },

  toJSON(message: WriteRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = message.key);
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<WriteRequest>): WriteRequest {
    return WriteRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<WriteRequest>): WriteRequest {
    const message = createBaseWriteRequest();
    message.key = object.key ?? "";
    message.value = object.value ?? 0;
    return message;
  },
};

function createBaseWriteResponse(): WriteResponse {
  return {};
}

export const WriteResponse = {
  encode(_: WriteResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): WriteResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseWriteResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(_: any): WriteResponse {
    return {};
  },

  toJSON(_: WriteResponse): unknown {
    const obj: any = {};
    return obj;
  },

  create(base?: DeepPartial<WriteResponse>): WriteResponse {
    return WriteResponse.fromPartial(base ?? {});
  },

  fromPartial(_: DeepPartial<WriteResponse>): WriteResponse {
    const message = createBaseWriteResponse();
    return message;
  },
};

export interface JepsenService {
  read(request: ReadRequest): Promise<ReadResponse>;
  write(request: WriteRequest): Promise<WriteResponse>;
}

export class JepsenServiceClientImpl implements JepsenService {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "org.example.JepsenService";
    this.rpc = rpc;
    this.read = this.read.bind(this);
    this.write = this.write.bind(this);
  }
  read(request: ReadRequest): Promise<ReadResponse> {
    const data = ReadRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Read", data);
    return promise.then((data) => ReadResponse.decode(_m0.Reader.create(data)));
  }

  write(request: WriteRequest): Promise<WriteResponse> {
    const data = WriteRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Write", data);
    return promise.then((data) => WriteResponse.decode(_m0.Reader.create(data)));
  }
}

interface Rpc {
  request(service: string, method: string, data: Uint8Array): Promise<Uint8Array>;
}

type ProtoMetaMessageOptions = {
  options?: { [key: string]: any };
  fields?: { [key: string]: { [key: string]: any } };
  oneof?: { [key: string]: { [key: string]: any } };
  nested?: { [key: string]: ProtoMetaMessageOptions };
};

export interface ProtoMetadata {
  fileDescriptor: FileDescriptorProto1;
  references: { [key: string]: any };
  dependencies?: ProtoMetadata[];
  options?: {
    options?: { [key: string]: any };
    services?: {
      [key: string]: { options?: { [key: string]: any }; methods?: { [key: string]: { [key: string]: any } } };
    };
    messages?: { [key: string]: ProtoMetaMessageOptions };
    enums?: { [key: string]: { options?: { [key: string]: any }; values?: { [key: string]: { [key: string]: any } } } };
  };
}

export const protoMetadata: ProtoMetadata = {
  fileDescriptor: FileDescriptorProto1.fromPartial({
    "name": "example.proto",
    "package": "org.example",
    "dependency": ["dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "ReadRequest",
      "field": [{
        "name": "key",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "key",
        "options": {
          "ctype": 0,
          "packed": false,
          "jstype": 0,
          "lazy": false,
          "deprecated": false,
          "weak": false,
          "uninterpretedOption": [],
        },
        "proto3Optional": false,
      }],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }, {
      "name": "ReadResponse",
      "field": [{
        "name": "value",
        "number": 1,
        "label": 1,
        "type": 5,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "value",
        "options": undefined,
        "proto3Optional": false,
      }],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }, {
      "name": "WriteRequest",
      "field": [{
        "name": "key",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "key",
        "options": {
          "ctype": 0,
          "packed": false,
          "jstype": 0,
          "lazy": false,
          "deprecated": false,
          "weak": false,
          "uninterpretedOption": [],
        },
        "proto3Optional": false,
      }, {
        "name": "value",
        "number": 2,
        "label": 1,
        "type": 5,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "value",
        "options": undefined,
        "proto3Optional": false,
      }],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }, {
      "name": "WriteResponse",
      "field": [],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }],
    "enumType": [],
    "service": [{
      "name": "JepsenService",
      "method": [{
        "name": "Read",
        "inputType": ".org.example.ReadRequest",
        "outputType": ".org.example.ReadResponse",
        "options": { "deprecated": false, "idempotencyLevel": 0, "uninterpretedOption": [] },
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Write",
        "inputType": ".org.example.WriteRequest",
        "outputType": ".org.example.WriteResponse",
        "options": { "deprecated": false, "idempotencyLevel": 0, "uninterpretedOption": [] },
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.org.example",
      "javaOuterClassname": "ExampleProto",
      "javaMultipleFiles": true,
      "javaGenerateEqualsAndHash": false,
      "javaStringCheckUtf8": false,
      "optimizeFor": 1,
      "goPackage": "",
      "ccGenericServices": false,
      "javaGenericServices": false,
      "pyGenericServices": false,
      "phpGenericServices": false,
      "deprecated": false,
      "ccEnableArenas": false,
      "objcClassPrefix": "OEX",
      "csharpNamespace": "Org.Example",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Org\\Example",
      "phpMetadataNamespace": "Org\\Example\\GPBMetadata",
      "rubyPackage": "Org::Example",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".org.example.ReadRequest": ReadRequest,
    ".org.example.ReadResponse": ReadResponse,
    ".org.example.WriteRequest": WriteRequest,
    ".org.example.WriteResponse": WriteResponse,
    ".org.example.JepsenService": JepsenServiceClientImpl,
  },
  dependencies: [protoMetadata1],
  options: {
    messages: {
      "ReadRequest": { fields: { "key": { "field": 0 } } },
      "WriteRequest": { fields: { "key": { "field": 0 } } },
    },
    services: { "JepsenService": { options: { "service_type": 1 }, methods: {} } },
  },
};

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}

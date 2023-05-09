/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata1 } from "./dev/restate/ext";

export const protobufPackage = "org.example";

export interface ReadRequest {
  key: number;
}

export interface ReadResponse {
  value: number;
}

export interface WriteRequest {
  key: number;
  value: number;
}

export interface WriteResponse {
}

export interface CasRequest {
  key: number;
  compare: number;
  exchange: number;
}

export interface CasResponse {
  success: boolean;
}

function createBaseReadRequest(): ReadRequest {
  return { key: 0 };
}

export const ReadRequest = {
  encode(message: ReadRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== 0) {
      writer.uint32(8).int32(message.key);
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
          if (tag != 8) {
            break;
          }

          message.key = reader.int32();
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
    return { key: isSet(object.key) ? Number(object.key) : 0 };
  },

  toJSON(message: ReadRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = Math.round(message.key));
    return obj;
  },

  create(base?: DeepPartial<ReadRequest>): ReadRequest {
    return ReadRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ReadRequest>): ReadRequest {
    const message = createBaseReadRequest();
    message.key = object.key ?? 0;
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
  return { key: 0, value: 0 };
}

export const WriteRequest = {
  encode(message: WriteRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== 0) {
      writer.uint32(8).int32(message.key);
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
          if (tag != 8) {
            break;
          }

          message.key = reader.int32();
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
    return { key: isSet(object.key) ? Number(object.key) : 0, value: isSet(object.value) ? Number(object.value) : 0 };
  },

  toJSON(message: WriteRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = Math.round(message.key));
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<WriteRequest>): WriteRequest {
    return WriteRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<WriteRequest>): WriteRequest {
    const message = createBaseWriteRequest();
    message.key = object.key ?? 0;
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

function createBaseCasRequest(): CasRequest {
  return { key: 0, compare: 0, exchange: 0 };
}

export const CasRequest = {
  encode(message: CasRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== 0) {
      writer.uint32(8).int32(message.key);
    }
    if (message.compare !== 0) {
      writer.uint32(16).int32(message.compare);
    }
    if (message.exchange !== 0) {
      writer.uint32(24).int32(message.exchange);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CasRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCasRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.key = reader.int32();
          continue;
        case 2:
          if (tag != 16) {
            break;
          }

          message.compare = reader.int32();
          continue;
        case 3:
          if (tag != 24) {
            break;
          }

          message.exchange = reader.int32();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CasRequest {
    return {
      key: isSet(object.key) ? Number(object.key) : 0,
      compare: isSet(object.compare) ? Number(object.compare) : 0,
      exchange: isSet(object.exchange) ? Number(object.exchange) : 0,
    };
  },

  toJSON(message: CasRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = Math.round(message.key));
    message.compare !== undefined && (obj.compare = Math.round(message.compare));
    message.exchange !== undefined && (obj.exchange = Math.round(message.exchange));
    return obj;
  },

  create(base?: DeepPartial<CasRequest>): CasRequest {
    return CasRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CasRequest>): CasRequest {
    const message = createBaseCasRequest();
    message.key = object.key ?? 0;
    message.compare = object.compare ?? 0;
    message.exchange = object.exchange ?? 0;
    return message;
  },
};

function createBaseCasResponse(): CasResponse {
  return { success: false };
}

export const CasResponse = {
  encode(message: CasResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.success === true) {
      writer.uint32(8).bool(message.success);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CasResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCasResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.success = reader.bool();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CasResponse {
    return { success: isSet(object.success) ? Boolean(object.success) : false };
  },

  toJSON(message: CasResponse): unknown {
    const obj: any = {};
    message.success !== undefined && (obj.success = message.success);
    return obj;
  },

  create(base?: DeepPartial<CasResponse>): CasResponse {
    return CasResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CasResponse>): CasResponse {
    const message = createBaseCasResponse();
    message.success = object.success ?? false;
    return message;
  },
};

export interface JepsenService {
  read(request: ReadRequest): Promise<ReadResponse>;
  write(request: WriteRequest): Promise<WriteResponse>;
  cas(request: CasRequest): Promise<CasResponse>;
}

export class JepsenServiceClientImpl implements JepsenService {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "org.example.JepsenService";
    this.rpc = rpc;
    this.read = this.read.bind(this);
    this.write = this.write.bind(this);
    this.cas = this.cas.bind(this);
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

  cas(request: CasRequest): Promise<CasResponse> {
    const data = CasRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Cas", data);
    return promise.then((data) => CasResponse.decode(_m0.Reader.create(data)));
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
        "type": 5,
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
        "type": 5,
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
    }, {
      "name": "CasRequest",
      "field": [{
        "name": "key",
        "number": 1,
        "label": 1,
        "type": 5,
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
        "name": "compare",
        "number": 2,
        "label": 1,
        "type": 5,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "compare",
        "options": undefined,
        "proto3Optional": false,
      }, {
        "name": "exchange",
        "number": 3,
        "label": 1,
        "type": 5,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "exchange",
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
      "name": "CasResponse",
      "field": [{
        "name": "success",
        "number": 1,
        "label": 1,
        "type": 8,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "success",
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
      }, {
        "name": "Cas",
        "inputType": ".org.example.CasRequest",
        "outputType": ".org.example.CasResponse",
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
    ".org.example.CasRequest": CasRequest,
    ".org.example.CasResponse": CasResponse,
    ".org.example.JepsenService": JepsenServiceClientImpl,
  },
  dependencies: [protoMetadata1],
  options: {
    messages: {
      "ReadRequest": { fields: { "key": { "field": 0 } } },
      "WriteRequest": { fields: { "key": { "field": 0 } } },
      "CasRequest": { fields: { "key": { "field": 0 } } },
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

from typing import Type

from cocas.ast_nodes import *
from cocas.code_block import Section
from cocas.default_code_segments import CodeSegmentsInterface
from cocas.default_instructions import TargetInstructionsInterface
from cocas.error import CdmExceptionTag

TAG = CdmExceptionTag.ASM


@dataclass
class Template:
    def __init__(self, sn: TemplateSectionNode, code_segments: Type[CodeSegmentsInterface],
                 target_instructions: Type[TargetInstructionsInterface]):
        self.name: str = sn.name
        self.labels: dict[str, int] = dict()

        size = 0
        for line in sn.lines:
            if isinstance(line, LabelDeclarationNode):
                label_name = line.label.name
                if label_name in self.labels:
                    raise Exception(f'Duplicate label "{label_name}" declaration')

                if line.external:
                    raise Exception('External labels not allowed in templates')
                elif line.entry:
                    raise Exception('Ents not allowed in templates')
                else:
                    self.labels[label_name] = size

            elif isinstance(line, InstructionNode):
                if line.mnemonic not in target_instructions.assembly_directives:
                    raise Exception('Only "dc" and "ds" allowed in templates')
                for seg in target_instructions.assemble_instruction(line):
                    size += seg.size

        self.labels['_'] = size


@dataclass
class ObjectSectionRecord:
    def __init__(self, section: Section, labels: dict[str, int], templates: dict[str, dict[str, int]]):
        self.address: int = section.address
        self.name: str = section.name
        self.data = bytearray()
        self.rell: set[int] = set()
        self.relh: set[tuple[int, int]] = set()
        self.ents: dict[str, int] = dict(p for p in section.labels.items() if p[0] in section.ents)
        self.xtrl: dict[str, list[int]] = dict()
        self.xtrh: dict[str, list[tuple[int, int]]] = dict()
        self.code_locations = section.code_locations

        for seg in section.segments:
            seg.fill(self, section, labels, templates)


@dataclass
class ObjectModule:
    def __init__(self):
        self.asects: list[ObjectSectionRecord] = []
        self.rsects: list[ObjectSectionRecord] = []


def gather_local_labels(sects: list[Section]):
    local_labels = dict()
    for sect in sects:
        local_labels.update({p for p in sect.labels.items() if not p[0].startswith('$')})
    return local_labels


def update_varying_length(section: Section, asects_labels: dict[str, int],
                          template_fields: dict[str, dict[str, int]]):
    labels = gather_local_labels([section])
    labels.update(asects_labels)
    pos = section.address
    for seg in section.segments:
        if isinstance(seg, CodeSegmentsInterface.VaryingLengthSegment):
            seg.update_varying_length(pos, section, labels, template_fields)
        pos += seg.size


def assemble(pn: ProgramNode, target_instructions, code_segments):
    templates = [Template(t, code_segments, target_instructions) for t in pn.template_sections]
    template_fields = dict([(t.name, t.labels) for t in templates])

    asects = [Section(asect, target_instructions, code_segments) for asect in pn.absolute_sections]
    rsects = [Section(rsect, target_instructions, code_segments) for rsect in pn.relocatable_sections]
    asects.sort(key=lambda s: s.address)

    asects_labels = gather_local_labels(asects)
    for asect in asects:
        update_varying_length(asect, asects_labels, template_fields)
        asects_labels.update(gather_local_labels([asect]))
    for rsect in rsects:
        update_varying_length(rsect, asects_labels, template_fields)

    obj = ObjectModule()
    obj.asects = [ObjectSectionRecord(asect, asects_labels, template_fields) for asect in asects]
    obj.rsects = [ObjectSectionRecord(rsect, asects_labels, template_fields) for rsect in rsects]

    return obj

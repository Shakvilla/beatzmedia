/// Wire DTO for taxonomy terms.
class TaxonomyTerm {
  const TaxonomyTerm({
    required this.id,
    required this.name,
    required this.kind,
  });

  final String id;
  final String name;
  final String kind;

  factory TaxonomyTerm.fromJson(Map<String, dynamic> json) => TaxonomyTerm(
        id: json['id'] as String,
        name: json['name'] as String,
        kind: json['kind'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'kind': kind,
      };
}
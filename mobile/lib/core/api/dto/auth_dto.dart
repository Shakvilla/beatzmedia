/// Wire DTO for auth requests/responses.
class LoginDto {
  const LoginDto({required this.email, required this.password});
  final String email;
  final String password;
}

class SignupDto {
  const SignupDto(
      {required this.name, required this.email, required this.password});
  final String name;
  final String email;
  final String password;
}

class TokenResponse {
  const TokenResponse({required this.token, required this.account});
  final String token;
  final AccountDto account;
}

class AccountDto {
  const AccountDto({
    required this.id,
    required this.name,
    required this.email,
    this.avatar,
    this.isArtist = false,
    this.isAdmin = false,
  });

  final String id;
  final String name;
  final String email;
  final String? avatar;
  final bool isArtist;
  final bool isAdmin;

  factory AccountDto.fromJson(Map<String, dynamic> json) => AccountDto(
        id: json['id'] as String,
        name: json['name'] as String,
        email: json['email'] as String,
        avatar: json['avatar'] as String?,
        isArtist: json['isArtist'] as bool? ?? false,
        isAdmin: json['isAdmin'] as bool? ?? false,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'email': email,
        if (avatar != null) 'avatar': avatar,
        'isArtist': isArtist,
        'isAdmin': isAdmin,
      };
}

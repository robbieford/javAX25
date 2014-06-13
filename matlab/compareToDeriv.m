function [y yprime] = compareToDeriv( file )
%UNTITLED2 Summary of this function goes here
%   Detailed explanation goes here

[y,FS,NBITS]=wavread(file);

%y = linspace(0, 10*pi, 100);

%y = sin(y);

y = y(:,1);

yprimeS = zeros(1, length(y));

for i=2 : length(y)-1
   yprimeS(i)  = y(i+1) - y(i-1);

end

yprime = diff(y);
figure,
grid on
hold on

x = [1:1:length(y)];
x2 = x(1,1:length(x)-1);
x3 = x(1,1:length(yprimeS));

plot(x,y,'-b',x3,yprimeS,'-r');
xlabel('Points');
ylabel('Amplitude');


%plot(x2,yprime, 'r');

legend('f(x)', 'f\prime(x)');


%plot(x3,yprimeS, 'g');
end